package com.vyapaaros.schemes.dto;

import com.vyapaaros.schemes.entity.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SchemeResponse(UUID id, UUID productId, UUID categoryId, String applicability,
    UUID retailerId, SchemeType schemeType, String name, String description, Integer buyQuantity, Integer freeQuantity,
    Integer minimumQuantity, BigDecimal discountPercentage, Instant startsAt, Instant endsAt,
    boolean active, String applicabilityDetails) {
    public static SchemeResponse from(ProductScheme s) {
        return new SchemeResponse(s.getId(), s.getProduct() == null ? null : s.getProduct().getId(),
            s.getCategory() == null ? null : s.getCategory().getId(),
            s.getProduct() != null ? "PRODUCT" : s.getCategory() != null ? "CATEGORY" : "DISTRIBUTOR",
            s.getRetailer() == null ? null : s.getRetailer().getId(), s.getSchemeType(), s.getName(),
            s.getDescription(), s.getBuyQuantity(), s.getFreeQuantity(), s.getMinimumQuantity(),
            s.getDiscountPercentage(), s.getStartsAt(), s.getEndsAt(), s.isActive(),
            s.getProduct() != null ? s.getProduct().getName() : s.getCategory() != null ? s.getCategory().getName() : "All distributor products");
    }
}
