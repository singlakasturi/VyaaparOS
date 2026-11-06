package com.vyapaaros.catalog.dto;

import com.vyapaaros.catalog.entity.ProductCategory;

import java.util.UUID;

public record CategoryResponse(
    UUID id,
    String name,
    String slug,
    String description
) {

    public static CategoryResponse from(ProductCategory category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getSlug(),
            category.getDescription()
        );
    }
}
