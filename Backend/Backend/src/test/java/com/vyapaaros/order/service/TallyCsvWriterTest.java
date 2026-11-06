package com.vyapaaros.order.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.vyapaaros.order.entity.*;
import com.vyapaaros.retailer.entity.Retailer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TallyCsvWriterTest {
    @Test
    void writesStableEscapedRowsAndLeavesUnknownGstBlank() {
        Retailer retailer = mock(Retailer.class);
        when(retailer.getBusinessName()).thenReturn("A, \"Party\"");
        when(retailer.getContactPhone()).thenReturn("+91 123");
        Order order = mock(Order.class);
        when(order.getCreatedAt()).thenReturn(Instant.parse("2026-01-02T00:00:00Z"));
        when(order.getOrderNumber()).thenReturn("ORD-1");
        when(order.getRetailer()).thenReturn(retailer);
        when(order.getFinalAmount()).thenReturn(new BigDecimal("10.00"));
        OrderItem item = mock(OrderItem.class);
        when(item.getProductSku()).thenReturn("=SKU");
        when(item.getProductName()).thenReturn("Tea, \"250 g\"");
        when(item.getPaidCases()).thenReturn(1);
        when(item.getFreeCases()).thenReturn(0);
        when(item.getTotalCases()).thenReturn(1);
        when(item.getUnitPricePerCase()).thenReturn(new BigDecimal("10.00"));
        when(item.getBaseAmount()).thenReturn(new BigDecimal("10.00"));
        when(item.getDiscountAmount()).thenReturn(BigDecimal.ZERO);
        when(item.getFinalAmount()).thenReturn(new BigDecimal("10.00"));
        when(item.getCurrency()).thenReturn("INR");
        when(item.getGstIncluded()).thenReturn(null);
        when(order.getItems()).thenReturn(List.of(item));

        String csv = new String(TallyCsvWriter.write(List.of(order)));
        assertTrue(csv.startsWith("Voucher Type,Voucher Date,Order Number"));
        assertTrue(csv.contains("'=SKU"));
        assertTrue(csv.contains("\"A, \"\"Party\"\"\""));
        assertTrue(csv.endsWith(",INR,\n"));
    }
}
