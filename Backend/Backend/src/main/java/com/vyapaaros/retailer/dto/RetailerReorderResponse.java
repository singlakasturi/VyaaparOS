package com.vyapaaros.retailer.dto;

import com.vyapaaros.order.dto.CartResponse;
import java.util.List;
import java.util.UUID;

public record RetailerReorderResponse(CartResponse cart, List<String> addedProducts,
    List<UnavailableProduct> unavailableProducts) {
    public record UnavailableProduct(UUID productId, String productName, String reason) { }
}
