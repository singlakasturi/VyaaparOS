package com.vyapaaros.catalog.dto;

import com.vyapaaros.catalog.entity.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ManagedProductResponse(UUID id,String sku,String name,String description,String brand,
  CategoryResponse category,String unitName,BigDecimal unitQuantity,int unitsPerCase,String caseDescription,
  boolean active,int availableCases,Instant createdAt,Instant updatedAt) {
    public static ManagedProductResponse from(Product p,int stock){return new ManagedProductResponse(p.getId(),p.getSku(),p.getName(),p.getDescription(),p.getBrand(),CategoryResponse.from(p.getCategory()),p.getUnitName(),p.getUnitQuantity(),p.getUnitsPerCase(),p.getCaseDescription(),p.isActive(),stock,p.getCreatedAt(),p.getUpdatedAt());}
}
