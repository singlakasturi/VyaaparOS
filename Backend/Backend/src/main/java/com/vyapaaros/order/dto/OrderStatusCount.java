package com.vyapaaros.order.dto;

import com.vyapaaros.order.entity.OrderStatus;

public record OrderStatusCount(OrderStatus status, long count) {}
