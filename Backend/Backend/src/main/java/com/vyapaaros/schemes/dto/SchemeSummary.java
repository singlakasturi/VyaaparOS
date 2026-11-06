package com.vyapaaros.schemes.dto;

import com.vyapaaros.schemes.entity.ProductScheme;
import com.vyapaaros.schemes.entity.SchemeType;
import java.math.BigDecimal;

public record SchemeSummary(boolean schemeAvailable, SchemeType schemeType, String schemeName,
    Integer buyQuantity, Integer freeQuantity, Integer minimumQuantity,
    BigDecimal discountPercentage) {
    public static SchemeSummary unavailable() { return new SchemeSummary(false, null, null, null, null, null, null); }
    public static SchemeSummary from(ProductScheme s) {
        return new SchemeSummary(true, s.getSchemeType(), s.getName(), s.getBuyQuantity(),
            s.getFreeQuantity(), s.getMinimumQuantity(), s.getDiscountPercentage());
    }
}
