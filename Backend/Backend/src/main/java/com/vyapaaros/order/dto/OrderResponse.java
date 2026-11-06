package com.vyapaaros.order.dto;
import com.vyapaaros.order.entity.Order;
import com.vyapaaros.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public record OrderResponse(UUID id,String orderNumber,UUID retailerId,UUID createdBy,OrderStatus status,
 BigDecimal baseAmount,BigDecimal discountAmount,BigDecimal finalAmount,String currency,String creditHoldReason,
 String notes,Instant createdAt,List<OrderItemResponse> items) {
 public static OrderResponse from(Order o){return new OrderResponse(o.getId(),o.getOrderNumber(),o.getRetailer().getId(),o.getCreatedBy().getId(),o.getStatus(),o.getBaseAmount(),o.getDiscountAmount(),o.getFinalAmount(),o.getCurrency(),o.getCreditHoldReason(),o.getNotes(),o.getCreatedAt(),o.getItems().stream().map(OrderItemResponse::from).toList());}
}
