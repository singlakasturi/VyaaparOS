package com.vyapaaros.pricing.dto;

import com.vyapaaros.pricing.entity.ProductPrice;

import java.math.BigDecimal;

public record ResolvedPrice(
    boolean priceAvailable,
    BigDecimal pricePerCase,
    String currency,
    PriceSource priceSource,
    Boolean gstIncluded
) {

    public static ResolvedPrice unavailable() {
        return new ResolvedPrice(false, null, null, null, null);
    }

    public static ResolvedPrice from(ProductPrice price, PriceSource source) {
        return new ResolvedPrice(
            true,
            price.getPricePerCase(),
            price.getCurrency(),
            source,
            price.getGstIncluded()
        );
    }
}
