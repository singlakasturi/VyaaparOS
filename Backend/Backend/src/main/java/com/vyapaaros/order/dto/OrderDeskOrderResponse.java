package com.vyapaaros.order.dto;

import com.vyapaaros.order.entity.Order;
import com.vyapaaros.order.entity.OrderStatus;
import com.vyapaaros.order.entity.FulfillmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record OrderDeskOrderResponse(UUID id, String orderNumber, UUID retailerId, String retailerName,
    String retailerContact, OrderStatus status, BigDecimal baseAmount, BigDecimal discountAmount,
    BigDecimal finalAmount, String currency, String creditHoldReason, String notes, Instant createdAt,
    List<OrderItemResponse> items, FulfillmentStatus fulfillmentStatus, String trackingReference,
    Instant fulfillmentStartedAt, Instant shippedAt, Instant deliveredAt) {
    public static OrderDeskOrderResponse from(Order o) {
        return new OrderDeskOrderResponse(o.getId(), o.getOrderNumber(), o.getRetailer().getId(),
            o.getRetailer().getBusinessName(), o.getRetailer().getContactPhone(), o.getStatus(),
            o.getBaseAmount(), o.getDiscountAmount(), o.getFinalAmount(), o.getCurrency(),
            o.getCreditHoldReason(), o.getNotes(), o.getCreatedAt(),
            o.getItems().stream().map(OrderItemResponse::from).toList(), o.getFulfillmentStatus(), o.getTrackingReference(),
            o.getFulfillmentStartedAt(), o.getShippedAt(), o.getDeliveredAt());
    }
}
