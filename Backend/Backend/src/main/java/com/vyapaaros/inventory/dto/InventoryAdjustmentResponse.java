package com.vyapaaros.inventory.dto;

import com.vyapaaros.inventory.entity.InventoryAdjustment;
import java.time.Instant;
import java.util.UUID;

public record InventoryAdjustmentResponse(UUID id,int quantityDelta,int quantityAfter,String reason,String actorName,Instant createdAt) {
    public static InventoryAdjustmentResponse from(InventoryAdjustment a){String name=a.getActor().getFullName();return new InventoryAdjustmentResponse(a.getId(),a.getQuantityDelta(),a.getQuantityAfter(),a.getReason(),name==null||name.isBlank()?"Account user":name,a.getCreatedAt());}
}
