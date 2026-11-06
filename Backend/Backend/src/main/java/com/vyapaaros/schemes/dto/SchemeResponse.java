package com.vyapaaros.schemes.dto;

import com.vyapaaros.schemes.entity.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SchemeResponse(UUID id, UUID productId, UUID retailerId, SchemeType schemeType,
    String name, String description, Integer buyQuantity, Integer freeQuantity,
    Integer minimumQuantity, BigDecimal discountPercentage, Instant startsAt, Instant endsAt,
    boolean active) {
    public static SchemeResponse from(ProductScheme s) {
        return new SchemeResponse(s.getId(), s.getProduct().getId(),
            s.getRetailer() == null ? null : s.getRetailer().getId(), s.getSchemeType(), s.getName(),
            s.getDescription(), s.getBuyQuantity(), s.getFreeQuantity(), s.getMinimumQuantity(),
            s.getDiscountPercentage(), s.getStartsAt(), s.getEndsAt(), s.isActive());
    }
}
