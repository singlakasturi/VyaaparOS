package com.vyapaaros.inventory.dto;

import com.vyapaaros.catalog.dto.CategoryResponse;
import com.vyapaaros.inventory.entity.Inventory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryResponse(UUID productId,String sku,String productName,CategoryResponse category,boolean active,
  int onHandCases,int reservedCases,int availableCases,int lowStockThreshold,boolean lowStock,Instant updatedAt,List<InventoryAdjustmentResponse> adjustments) {
    public static InventoryResponse from(Inventory i,List<InventoryAdjustmentResponse> history){var p=i.getProduct();return new InventoryResponse(p.getId(),p.getSku(),p.getName(),CategoryResponse.from(p.getCategory()),p.isActive(),i.getOnHandCases(),i.getReservedCases(),i.getAvailableCases(),i.getLowStockThreshold(),i.getAvailableCases()<=i.getLowStockThreshold(),i.getUpdatedAt(),history);}
}
