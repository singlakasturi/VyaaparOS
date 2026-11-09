package com.vyapaaros.retailer.service;

import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.order.repository.OrderRepository;
import com.vyapaaros.order.service.CartService;
import com.vyapaaros.order.service.OrderScopeService;
import com.vyapaaros.retailer.dto.RetailerReorderResponse;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetailerOrderReorderService {
    private final OrderRepository orders;
    private final OrderScopeService scope;
    private final CartService carts;

    public RetailerOrderReorderService(OrderRepository orders, OrderScopeService scope, CartService carts) {
        this.orders = orders;
        this.scope = scope;
        this.carts = carts;
    }

    @Transactional
    public RetailerReorderResponse reorder(Authentication authentication, UUID orderId) {
        var historicalOrder = orders.findDetailed(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        UUID retailerId = historicalOrder.getRetailer().getId();
        scope.requireHistoricalOrderAccess(scope.userId(authentication), retailerId, authentication);
        UUID activeDistributorId = scope.currentDistributorForRetailer(retailerId).getId();
        if (!activeDistributorId.equals(historicalOrder.getDistributor().getId())) {
            throw new ResourceConflictException("This order belongs to a previous distributor. Reorder from the current distributor catalogue.");
        }

        List<CartService.ReorderItem> items = historicalOrder.getItems().stream()
            .map(item -> new CartService.ReorderItem(item.getProduct().getId(), item.getPaidCases()))
            .toList();
        CartService.ReorderCartResult result = carts.addReorderItems(authentication, retailerId, items);
        var itemNames = historicalOrder.getItems().stream().collect(Collectors.toMap(
            item -> item.getProduct().getId(), item -> item.getProductName(), (first, ignored) -> first));
        List<RetailerReorderResponse.UnavailableProduct> unavailable = result.skippedProducts().stream()
            .map(item -> new RetailerReorderResponse.UnavailableProduct(item.productId(),
                itemNames.getOrDefault(item.productId(), "Product"), item.reason()))
            .toList();
        return new RetailerReorderResponse(result.cart(), result.addedProducts(), unavailable);
    }
}
