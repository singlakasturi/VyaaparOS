package com.vyapaaros.reports.repository;

import com.vyapaaros.order.entity.OrderStatus;

public interface StatusOrderAggregate {
    OrderStatus getStatus();
    Long getOrderCount();
}
