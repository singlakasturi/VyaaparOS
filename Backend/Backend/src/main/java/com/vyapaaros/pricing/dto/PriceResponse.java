package com.vyapaaros.pricing.dto;

import com.vyapaaros.pricing.entity.ProductPrice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PriceResponse(
    UUID id,
    UUID productId,
    UUID retailerId,
    BigDecimal pricePerCase,
    String currency,
    Boolean gstIncluded,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {

    public static PriceResponse from(ProductPrice price) {
        return new PriceResponse(
            price.getId(),
            price.getProduct().getId(),
            price.getRetailer() == null ? null : price.getRetailer().getId(),
            price.getPricePerCase(),
            price.getCurrency(),
            price.getGstIncluded(),
            price.isActive(),
            price.getCreatedAt(),
            price.getUpdatedAt()
        );
    }
}
