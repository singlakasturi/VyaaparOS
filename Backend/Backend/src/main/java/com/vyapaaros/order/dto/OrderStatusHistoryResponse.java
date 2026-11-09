package com.vyapaaros.order.dto;

import com.vyapaaros.order.entity.OrderStatusHistory;
import com.vyapaaros.order.entity.OrderHistoryEventType;
import com.vyapaaros.order.entity.FulfillmentStatus;
import com.vyapaaros.order.entity.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record OrderStatusHistoryResponse(UUID id, OrderStatus previousStatus, OrderStatus newStatus,
    UUID changedBy, String reason, Instant createdAt, OrderHistoryEventType eventType,
    FulfillmentStatus previousFulfillmentStatus, FulfillmentStatus newFulfillmentStatus, String trackingReference,
    UUID requestId, String requestStatus) {
    public static OrderStatusHistoryResponse from(OrderStatusHistory h) {
        return new OrderStatusHistoryResponse(h.getId(), h.getPreviousStatus(), h.getNewStatus(),
            h.getChangedBy().getId(), h.getReason(), h.getCreatedAt(), h.getEventType(),
            h.getPreviousFulfillmentStatus(), h.getNewFulfillmentStatus(), h.getTrackingReference(),
            h.getRequestId(), h.getRequestStatus());
    }
}
