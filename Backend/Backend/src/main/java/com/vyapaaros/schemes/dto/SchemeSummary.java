package com.vyapaaros.schemes.dto;

import com.vyapaaros.schemes.entity.ProductScheme;
import com.vyapaaros.schemes.entity.SchemeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SchemeSummary(boolean schemeAvailable, UUID schemeId, SchemeType schemeType, String schemeName,
    String description, Instant startsAt, Instant endsAt, String applicability,
    Integer buyQuantity, Integer freeQuantity, Integer minimumQuantity,
    BigDecimal discountPercentage) {
    public static SchemeSummary unavailable() { return new SchemeSummary(false, null, null, null, null, null, null, null, null, null, null, null); }
    public static SchemeSummary from(ProductScheme s) {
        return new SchemeSummary(true, s.getId(), s.getSchemeType(), s.getName(), s.getDescription(), s.getStartsAt(), s.getEndsAt(),
            s.getProduct() != null ? "PRODUCT" : s.getCategory() != null ? "CATEGORY" : "DISTRIBUTOR", s.getBuyQuantity(),
            s.getFreeQuantity(), s.getMinimumQuantity(), s.getDiscountPercentage());
    }
}
