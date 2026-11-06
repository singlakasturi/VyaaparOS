package com.vyapaaros.order.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.common.exception.*;
import com.vyapaaros.common.exception.InsufficientStockException.StockIssue;
import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.order.dto.*;
import com.vyapaaros.order.entity.*;
import com.vyapaaros.order.repository.*;
import com.vyapaaros.pricing.dto.ResolvedPrice;
import com.vyapaaros.pricing.service.PriceResolutionService;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.entity.RetailerStatus;
import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.schemes.dto.SchemeEvaluation;
import com.vyapaaros.schemes.service.SchemeResolutionService;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final CartRepository carts;
    private final RetailerRepository retailers;
    private final UserRepository users;
    private final OrderScopeService scope;
    private final PriceResolutionService prices;
    private final SchemeResolutionService schemes;
    private final InventoryRepository inventory;
    private final ProductRepository products;

    public OrderService(
        OrderRepository o,
        CartRepository c,
        RetailerRepository r,
        UserRepository u,
        OrderScopeService s,
        PriceResolutionService p,
        SchemeResolutionService sc,
        InventoryRepository i,
        ProductRepository pr
    ) {
        orders = o;
        carts = c;
        retailers = r;
        users = u;
        scope = s;
        prices = p;
        schemes = sc;
        inventory = i;
        products = pr;
    }

    private record LineItemSpec(Product product, int paidCases) {}
    private record PricedLine(LineItemSpec line, ResolvedPrice price, SchemeEvaluation calculation, int totalCases) {}

    @Transactional
    public OrderResponse place(Authentication a, OrderRequest request) {
        UUID uid = scope.userId(a);
        scope.require(uid, request.retailerId(), a);
        Retailer retailer = retailers.findById(request.retailerId())
            .orElseThrow(() -> new ResourceNotFoundException("Retailer not found."));
        if (retailer.getStatus() != RetailerStatus.ACTIVE) {
            throw new ResourceConflictException("Retailer is not active.");
        }
        var distributor = scope.currentDistributorForRetailer(retailer.getId());

        // Idempotency: return existing order if same client_order_id was already placed
        Optional<Order> existing = orders.findByRetailerIdAndClientOrderId(retailer.getId(), request.clientOrderId());
        if (existing.isPresent()) {
            if (!distributor.getId().equals(existing.get().getDistributor().getId())) {
                throw new com.vyapaaros.common.exception.ResourceNotFoundException("Order not found.");
            }
            return OrderResponse.from(orders.findDetailed(existing.get().getId()).orElse(existing.get()));
        }

        List<LineItemSpec> lines;
        Cart cartToCheckout = null;

        // Support direct items (e.g. from offline draft sync) or fallback to active cart
        if (request.items() != null && !request.items().isEmpty()) {
            Set<UUID> seenProductIds = new HashSet<>();
            lines = new ArrayList<>();
            for (OrderItemRequest reqItem : request.items()) {
                if (!seenProductIds.add(reqItem.productId())) {
                    throw new ResourceConflictException("Duplicate product lines are not allowed.");
                }
                Product product = products.findByIdAndDistributorIdAndActiveTrue(reqItem.productId(), distributor.getId())
                    .filter(p -> p.getCategory().isActive())
                    .orElseThrow(() -> new ResourceNotFoundException("Active product not found: " + reqItem.productId()));
                lines.add(new LineItemSpec(product, reqItem.paidCases()));
            }
        } else {
            Cart cart = carts.findActive(uid, retailer.getId())
                .orElseThrow(() -> new ResourceConflictException("Cart is empty."));
            if (cart.getItems().isEmpty()) {
                throw new ResourceConflictException("Cart is empty.");
            }
            lines = cart.getItems().stream()
                .map(i -> new LineItemSpec(i.getProduct(), i.getPaidCases()))
                .toList();
            cartToCheckout = cart;
        }

        List<UUID> ids = lines.stream().map(l -> l.product().getId()).toList();
        Map<UUID, ResolvedPrice> resolved = prices.resolve(a, retailer.getId(), ids);

        List<PricedLine> pricedLines = new ArrayList<>();
        Map<UUID, Integer> stockByProduct = inventory.findByProductIdInAndDistributorId(
            lines.stream().map(line -> line.product().getId()).toList(), distributor.getId()
        ).stream().collect(java.util.stream.Collectors.toMap(
            row -> row.getProduct().getId(), row -> row.getAvailableCases()
        ));
        List<StockIssue> stockIssues = new ArrayList<>();
        for (LineItemSpec line : lines) {
            ResolvedPrice price = resolved.getOrDefault(line.product().getId(), ResolvedPrice.unavailable());
            if (!price.priceAvailable()) {
                throw new ResourceConflictException("Price unavailable for " + line.product().getSku() + ".");
            }
            SchemeEvaluation calc = schemes.evaluate(line.product().getId(), retailer.getId(), line.paidCases(), price, a);
            if (!calc.priceAvailable()) {
                throw new ResourceConflictException("Price unavailable for " + line.product().getSku() + ".");
            }
            int total = line.paidCases() + calc.freeCases();
            int available = stockByProduct.getOrDefault(line.product().getId(), 0);
            if (total > available) {
                stockIssues.add(new StockIssue(line.product().getSku(), line.product().getName(), total, available));
            }
            pricedLines.add(new PricedLine(line, price, calc, total));
        }
        if (!stockIssues.isEmpty()) throw new InsufficientStockException(stockIssues);

        // Atomic guarded updates serialize competing orders. If any reservation loses a race,
        // throwing from this transaction rolls back every earlier reservation in this order.
        for (PricedLine line : pricedLines) {
            if (inventory.reserve(line.line().product().getId(), distributor.getId(), line.totalCases()) != 1) {
                int available = inventory.findAvailableCasesByProductIdAndDistributorId(
                    line.line().product().getId(), distributor.getId()).orElse(0);
                throw new InsufficientStockException(List.of(new StockIssue(
                    line.line().product().getSku(), line.line().product().getName(), line.totalCases(), available
                )));
            }
        }

        Order order = new Order();
        order.setOrderNumber("ORD-" + orders.nextNumber());
        order.setRetailer(retailer);
        order.setDistributor(distributor);
        order.setCreatedBy(users.findById(uid).orElseThrow(() -> new ResourceNotFoundException("User not found.")));
        order.setClientOrderId(request.clientOrderId());
        order.setCurrency("INR");
        order.setNotes(request.notes());
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        BigDecimal base = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;

        int itemPosition = 0;
        for (PricedLine priced : pricedLines) {
            var line = priced.line();
            ResolvedPrice price = priced.price();
            SchemeEvaluation calc = priced.calculation();
            int total = priced.totalCases();

            OrderItem item = new OrderItem();
            Product p = line.product();
            item.setProduct(p);
            item.setProductSku(p.getSku());
            item.setProductName(p.getName());
            item.setPaidCases(line.paidCases());
            item.setPosition(itemPosition++);
            item.setFreeCases(calc.freeCases());
            item.setTotalCases(total);
            item.setUnitPricePerCase(price.pricePerCase().setScale(2, RoundingMode.HALF_UP));
            item.setCurrency(price.currency());
            item.setGstIncluded(price.gstIncluded());
            item.setBaseAmount(calc.baseAmount().setScale(2, RoundingMode.HALF_UP));
            item.setDiscountAmount(calc.discountAmount().setScale(2, RoundingMode.HALF_UP));
            item.setFinalAmount(calc.finalAmount().setScale(2, RoundingMode.HALF_UP));
            item.setAppliedSchemeId(calc.schemeId());
            item.setSchemeName(calc.schemeName());
            item.setSchemeType(calc.schemeType() == null ? null : calc.schemeType().name());
            item.setCreatedAt(Instant.now());

            order.addItem(item);
            base = base.add(item.getBaseAmount());
            discount = discount.add(item.getDiscountAmount());
        }

        BigDecimal finalAmount = base.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        order.setBaseAmount(base.setScale(2, RoundingMode.HALF_UP));
        order.setDiscountAmount(discount.setScale(2, RoundingMode.HALF_UP));
        order.setFinalAmount(finalAmount);

        if (retailer.getOutstandingBalance().add(finalAmount).compareTo(retailer.getCreditLimit()) > 0) {
            order.setStatus(OrderStatus.CREDIT_HOLD);
            BigDecimal exposure = retailer.getOutstandingBalance().add(finalAmount).setScale(2, RoundingMode.HALF_UP);
            BigDecimal limit = retailer.getCreditLimit().setScale(2, RoundingMode.HALF_UP);
            order.setCreditHoldReason("Projected credit exposure " + exposure.toPlainString()
                + " INR exceeds the configured credit limit " + limit.toPlainString() + " INR.");
        } else {
            order.setStatus(OrderStatus.SUBMITTED);
        }

        try {
            Order saved = orders.save(order);
            if (cartToCheckout != null) {
                cartToCheckout.getItems().clear();
                cartToCheckout.setStatus(CartStatus.CHECKED_OUT);
                cartToCheckout.setUpdatedAt(Instant.now());
                carts.save(cartToCheckout);
            }
            return OrderResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            return OrderResponse.from(orders.findByRetailerIdAndClientOrderId(retailer.getId(), request.clientOrderId()).orElseThrow(() -> e));
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Authentication a, UUID id) {
        Order o = orders.findDetailed(id).orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        scope.require(scope.userId(a), o.getRetailer().getId(), a);
        return OrderResponse.from(o);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(Authentication a, UUID retailerId, Pageable pageable) {
        UUID uid = scope.userId(a);
        scope.require(uid, retailerId, a);
        return orders.findByRetailerId(retailerId, pageable).map(OrderResponse::from);
    }
}
