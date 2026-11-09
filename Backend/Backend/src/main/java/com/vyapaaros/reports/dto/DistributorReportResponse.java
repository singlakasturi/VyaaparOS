package com.vyapaaros.reports.dto;

import com.vyapaaros.inventory.entity.InventoryMovementType;
import com.vyapaaros.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DistributorReportResponse(LocalDate fromDate, LocalDate toDate, String timezone,
    String activityStatusRule, String topProductMetric,
    List<DailyOrderActivity> orderActivity, List<ProductRanking> topProducts,
    List<OrderStatusTotal> orderStatuses, InventoryMovementSummary inventoryMovements,
    String movementCoverageRule) {
    public record DailyOrderActivity(LocalDate day, String currency, long orderCount, BigDecimal orderValue) { }
    public record ProductRanking(String productName, String currency, long orderedCases, BigDecimal orderValue, int rank) { }
    public record OrderStatusTotal(OrderStatus status, long orderCount) { }
    public record MovementTypeTotal(InventoryMovementType movementType, long movementCount,
        long onHandDeltaCases, long reservedDeltaCases) { }
    public record InventoryMovementSummary(long physicalInwardCases, long physicalOutwardCases,
        long reservationCreatedCases, long reservationReleasedCases, long deliveryConsumedCases,
        List<MovementTypeTotal> byMovementType) { }
}
