package com.vyapaaros.order.dto;

import com.vyapaaros.order.entity.OrderStatus;
import java.util.List;
import java.util.Map;

public record OrderDeskOverviewResponse(Map<OrderStatus, Long> statusCounts,
    long totalOrders, List<OrderDeskOrderResponse> recentOrders) {}
