package com.vyapaaros.pricing.dto;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.pricing.entity.ProductPrice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ManagedPriceResponse(UUID productId, String sku, String productName, boolean productActive,
    BigDecimal pricePerCase, String currency, Boolean gstIncluded, boolean priceActive,
    Instant updatedAt) {
    public static ManagedPriceResponse from(ProductPrice price) {
        Product product = price.getProduct();
        return new ManagedPriceResponse(product.getId(), product.getSku(), product.getName(), product.isActive(),
            price.getPricePerCase(), price.getCurrency(), price.getGstIncluded(), price.isActive(), price.getUpdatedAt());
    }

    public static ManagedPriceResponse unpriced(Product product) {
        return new ManagedPriceResponse(product.getId(), product.getSku(), product.getName(), product.isActive(),
            null, "INR", null, false, null);
    }
}
