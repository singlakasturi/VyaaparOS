package com.vyapaaros.retailer.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.order.service.CartService;
import com.vyapaaros.retailer.dto.QuickOrderRequest;
import com.vyapaaros.retailer.dto.QuickOrderResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetailerQuickOrderService {
    private final RetailerAuthorizationService retailerAuthorization;
    private final DistributorAuthorizationService distributorAuthorization;
    private final ProductRepository products;
    private final CartService carts;

    public RetailerQuickOrderService(RetailerAuthorizationService retailerAuthorization,
        DistributorAuthorizationService distributorAuthorization, ProductRepository products, CartService carts) {
        this.retailerAuthorization = retailerAuthorization;
        this.distributorAuthorization = distributorAuthorization;
        this.products = products;
        this.carts = carts;
    }

    @Transactional
    public QuickOrderResponse addRows(Authentication authentication, UUID retailerId, QuickOrderRequest request) {
        UUID userId = userId(authentication);
        retailerAuthorization.requireMembership(userId, retailerId);
        DistributorBusiness distributor = distributorAuthorization.requireCurrentDistributorForRetailer(retailerId);

        List<QuickOrderRequest.QuickOrderRow> rows = request.rows();
        Map<String, Long> codeCounts = rows.stream().filter(row -> row != null)
            .map(row -> normalize(row.productCode())).filter(code -> !code.isEmpty())
            .collect(Collectors.groupingBy(code -> code, Collectors.counting()));
        List<QuickOrderResponse.RowOutcome> results = new ArrayList<>();
        List<CartService.ReorderItem> validItems = new ArrayList<>();
        Map<UUID, QuickOrderRequest.QuickOrderRow> sourceRows = new HashMap<>();
        Map<UUID, Integer> rowNumbers = new HashMap<>();
        Map<Integer, Product> resolvedProducts = new HashMap<>();

        for (int index = 0; index < rows.size(); index++) {
            QuickOrderRequest.QuickOrderRow row = rows.get(index);
            int rowNumber = index + 1;
            if (row == null) {
                results.add(notAdded(rowNumber, null, null, null, null, "Enter a product code and quantity."));
                continue;
            }
            String code = normalize(row.productCode());
            if (code.isEmpty()) {
                results.add(notAdded(rowNumber, row.productCode(), null, null, parseQuantity(row.quantity()), "Product code is required."));
                continue;
            }
            if (code.length() > 60) {
                results.add(notAdded(rowNumber, row.productCode(), null, null, parseQuantity(row.quantity()), "Product codes cannot exceed 60 characters."));
                continue;
            }
            if (codeCounts.getOrDefault(code, 0L) > 1) {
                results.add(notAdded(rowNumber, row.productCode(), null, null, parseQuantity(row.quantity()), "Duplicate product code. Enter each code once."));
                continue;
            }
            Integer quantity = parseQuantity(row.quantity());
            if (quantity == null || quantity <= 0) {
                results.add(notAdded(rowNumber, row.productCode(), null, null, quantity, "Quantity must be a positive whole number of cases."));
                continue;
            }

            Product product = products.findByDistributorIdAndSkuIgnoreCase(distributor.getId(), code).orElse(null);
            if (product == null) {
                results.add(notAdded(rowNumber, row.productCode(), null, null, quantity, "Product code was not found in the current distributor catalogue."));
                continue;
            }
            if (!product.isActive() || !product.getCategory().isActive()) {
                results.add(notAdded(rowNumber, row.productCode(), product.getId(), product.getName(), quantity, "This product is no longer active in the current catalogue."));
                continue;
            }
            validItems.add(new CartService.ReorderItem(product.getId(), quantity));
            sourceRows.put(product.getId(), row);
            rowNumbers.put(product.getId(), rowNumber);
            resolvedProducts.put(rowNumber, product);
        }

        CartService.ReorderCartResult cartResult = carts.addReorderItems(authentication, retailerId, validItems);
        Map<UUID, CartService.ReorderSkippedItem> skippedByProduct = cartResult.skippedProducts().stream()
            .collect(Collectors.toMap(CartService.ReorderSkippedItem::productId, skipped -> skipped));
        for (CartService.ReorderItem item : validItems) {
            int rowNumber = rowNumbers.get(item.productId());
            Product product = resolvedProducts.get(rowNumber);
            QuickOrderRequest.QuickOrderRow row = sourceRows.get(item.productId());
            CartService.ReorderSkippedItem skipped = skippedByProduct.get(item.productId());
            results.add(new QuickOrderResponse.RowOutcome(rowNumber, row.productCode(), product.getId(),
                product.getName(), item.paidCases(), skipped == null ? "ADDED" : "NOT_ADDED",
                skipped == null ? "Added to your current cart." : skipped.reason()));
        }
        results.sort(java.util.Comparator.comparingInt(QuickOrderResponse.RowOutcome::rowNumber));
        return new QuickOrderResponse(cartResult.cart(), List.copyOf(results));
    }

    private QuickOrderResponse.RowOutcome notAdded(int rowNumber, String code, UUID productId,
        String name, Integer quantity, String message) {
        return new QuickOrderResponse.RowOutcome(rowNumber, code, productId, name, quantity, "NOT_ADDED", message);
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private Integer parseQuantity(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value.trim()); }
        catch (NumberFormatException exception) { return null; }
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof UUID userId)
            || authentication.getAuthorities().stream().noneMatch(authority -> "ROLE_RETAILER".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Retailer quick-order access denied.");
        }
        return userId;
    }
}
