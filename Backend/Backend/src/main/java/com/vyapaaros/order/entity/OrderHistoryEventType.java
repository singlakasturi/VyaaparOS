package com.vyapaaros.order.entity;

public enum OrderHistoryEventType {
    ORDER_STATUS,
    FULFILLMENT,
    CANCELLATION_REQUEST,
    CANCELLATION_DECISION,
    RETURN_REQUEST,
    RETURN_DECISION
}
