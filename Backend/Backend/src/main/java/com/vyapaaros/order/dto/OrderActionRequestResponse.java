package com.vyapaaros.order.dto;

import com.vyapaaros.order.entity.OrderActionRequest;
import com.vyapaaros.order.entity.OrderActionRequestStatus;
import com.vyapaaros.order.entity.OrderActionRequestType;
import java.time.Instant;
import java.util.UUID;

public record OrderActionRequestResponse(UUID id, UUID orderId, String orderNumber, UUID retailerId,
    String retailerName, OrderActionRequestType requestType, OrderActionRequestStatus status,
    String reason, Instant createdAt, Instant decidedAt, String decisionComment) {
    public static OrderActionRequestResponse from(OrderActionRequest request) {
        var order=request.getOrder();
        return new OrderActionRequestResponse(request.getId(),order.getId(),order.getOrderNumber(),
            order.getRetailer().getId(),order.getRetailer().getBusinessName(),request.getRequestType(),request.getStatus(),
            request.getReason(),request.getCreatedAt(),request.getDecidedAt(),request.getDecisionComment());
    }
}
