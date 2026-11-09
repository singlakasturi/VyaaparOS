package com.vyapaaros.retailer.dto;

import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.order.entity.Order;
import com.vyapaaros.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RetailerStatementResponse(
    UUID distributorId, String distributorName, LocalDate fromDate, LocalDate toDate, String timezone,
    List<OrderActivity> orders, List<CurrencyTotal> orderActivityTotals
) {
    public static RetailerStatementResponse from(DistributorBusiness distributor, LocalDate from, LocalDate to,
        List<Order> orders, List<CurrencyTotal> totals) {
        return new RetailerStatementResponse(distributor.getId(), distributor.getBusinessName(), from, to, "UTC",
            orders.stream().map(OrderActivity::from).toList(), totals);
    }

    public record OrderActivity(UUID id, String orderNumber, Instant createdAt, OrderStatus status,
        BigDecimal baseAmount, BigDecimal discountAmount, BigDecimal finalAmount, String currency,
        List<ItemActivity> items) {
        static OrderActivity from(Order order) {
            return new OrderActivity(order.getId(), order.getOrderNumber(), order.getCreatedAt(), order.getStatus(),
                order.getBaseAmount(), order.getDiscountAmount(), order.getFinalAmount(), order.getCurrency(),
                order.getItems().stream().map(ItemActivity::from).toList());
        }
    }

    public record ItemActivity(UUID productId, String sku, String name, int paidCases, int freeCases,
        int totalCases, BigDecimal unitPricePerCase, BigDecimal baseAmount, BigDecimal discountAmount,
        BigDecimal finalAmount, String currency, Boolean gstIncluded, String schemeName) {
        static ItemActivity from(com.vyapaaros.order.entity.OrderItem item) {
            return new ItemActivity(item.getProduct().getId(), item.getProductSku(), item.getProductName(),
                item.getPaidCases(), item.getFreeCases(), item.getTotalCases(), item.getUnitPricePerCase(),
                item.getBaseAmount(), item.getDiscountAmount(), item.getFinalAmount(), item.getCurrency(),
                item.getGstIncluded(), item.getSchemeName());
        }
    }

    public record CurrencyTotal(String currency, BigDecimal orderActivityTotal) { }
}
