package com.vyapaaros.catalog.dto;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.pricing.dto.ResolvedPrice;
import com.vyapaaros.schemes.dto.SchemeSummary;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    String sku,
    String name,
    String description,
    String brand,
    CategoryResponse category,
    String unitName,
    BigDecimal unitQuantity,
    int unitsPerCase,
    String caseDescription,
    int availableCases,
    boolean priceAvailable,
    BigDecimal pricePerCase,
    String currency,
    String priceSource,
    Boolean gstIncluded,
    boolean schemeAvailable,
    String schemeType,
    String schemeName,
    Integer buyQuantity,
    Integer freeQuantity,
    Integer minimumQuantity,
    BigDecimal discountPercentage
) {

    public static ProductResponse from(Product product, int availableCases) {
        return from(product, availableCases, ResolvedPrice.unavailable());
    }

    public static ProductResponse from(
        Product product,
        int availableCases,
        ResolvedPrice price
    ) {
        return from(product, availableCases, price, SchemeSummary.unavailable());
    }

    public static ProductResponse from(
        Product product,
        int availableCases,
        ResolvedPrice price,
        SchemeSummary scheme
    ) {
        return new ProductResponse(
            product.getId(),
            product.getSku(),
            product.getName(),
            product.getDescription(),
            product.getBrand(),
            CategoryResponse.from(product.getCategory()),
            product.getUnitName(),
            product.getUnitQuantity(),
            product.getUnitsPerCase(),
            product.getCaseDescription(),
            availableCases,
            price.priceAvailable(),
            price.pricePerCase(),
            price.currency(),
            price.priceSource() == null ? null : price.priceSource().name(),
            price.gstIncluded(),
            scheme.schemeAvailable(),
            scheme.schemeType() == null ? null : scheme.schemeType().name(),
            scheme.schemeName(),
            scheme.buyQuantity(),
            scheme.freeQuantity(),
            scheme.minimumQuantity(),
            scheme.discountPercentage()
        );
    }
}
