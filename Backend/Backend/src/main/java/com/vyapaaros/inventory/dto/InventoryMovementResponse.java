package com.vyapaaros.inventory.dto;

import com.vyapaaros.inventory.entity.InventoryMovement;
import com.vyapaaros.inventory.entity.InventoryMovementType;
import java.time.Instant;
import java.util.UUID;

public record InventoryMovementResponse(UUID id, UUID productId, String productName, String sku,
    InventoryMovementType movementType, int onHandDelta, int reservedDelta, Integer onHandAfter,
    Integer reservedAfter, Integer availableAfter, UUID orderId, String orderNumber,
    String actorName, String reference, String reason, Instant createdAt) {
    public static InventoryMovementResponse from(InventoryMovement m) {
        String actor = m.getActor() == null ? null : m.getActor().getFullName();
        return new InventoryMovementResponse(m.getId(), m.getProduct().getId(), m.getProduct().getName(),
            m.getProduct().getSku(), m.getMovementType(), m.getOnHandDelta(), m.getReservedDelta(),
            m.getOnHandAfter(), m.getReservedAfter(), m.getAvailableAfter(),
            m.getOrder() == null ? null : m.getOrder().getId(),
            m.getOrder() == null ? null : m.getOrder().getOrderNumber(),
            actor == null || actor.isBlank() ? null : actor, m.getReference(), m.getReason(), m.getCreatedAt());
    }
}
