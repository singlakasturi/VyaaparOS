package com.vyapaaros.reports.repository;

import java.math.BigDecimal;

public interface ProductOrderAggregate {
    String getProductName();
    String getCurrency();
    Long getOrderedCases();
    BigDecimal getOrderValue();
}
