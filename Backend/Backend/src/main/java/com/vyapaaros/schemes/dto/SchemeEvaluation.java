package com.vyapaaros.schemes.dto;

import com.vyapaaros.schemes.entity.SchemeType;
import java.math.BigDecimal;
import java.util.UUID;

public record SchemeEvaluation(UUID schemeId, String schemeName, SchemeType schemeType,
    int paidCases, int freeCases, BigDecimal discountPercentage, BigDecimal discountAmount,
    BigDecimal baseAmount, BigDecimal finalAmount, String explanation, boolean priceAvailable) {
    public static SchemeEvaluation unavailable(int paidCases) {
        return new SchemeEvaluation(null, null, null, paidCases, 0, null, null, null, null,
            "Price unavailable; scheme cannot be evaluated.", false);
    }
}
