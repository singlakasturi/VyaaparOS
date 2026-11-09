package com.vyapaaros.reports.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyOrderAggregate {
    LocalDate getDay();
    String getCurrency();
    Long getOrderCount();
    BigDecimal getOrderValue();
}
