package com.vyapaaros.reports.service;

import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.inventory.entity.InventoryMovementType;
import com.vyapaaros.reports.dto.DistributorReportResponse;
import com.vyapaaros.reports.repository.*;
import com.vyapaaros.order.entity.OrderStatus;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DistributorReportService {
    private static final ZoneOffset REPORT_ZONE = ZoneOffset.UTC;
    private static final int MAX_RANGE_DAYS = 366;
    private static final String ACTIVITY_RULE = "Order activity and top products include SUBMITTED, CREDIT_HOLD, APPROVED, and TALLY_EXPORTED orders; REJECTED and CANCELLED orders are excluded. Order status totals include every existing status in the range. Amounts are persisted gross snapshot values, not realized sales or payments.";
    private static final String MOVEMENT_COVERAGE_RULE = "Movement totals include ledger records in the selected period. Historical order reservations and deliveries before movement tracking began were not reconstructed; existing manual adjustment records may be backfilled.";

    private final DistributorAuthorizationService authorization;
    private final DistributorReportRepository orders;
    private final DistributorMovementReportRepository movements;

    public DistributorReportService(DistributorAuthorizationService authorization,
        DistributorReportRepository orders, DistributorMovementReportRepository movements) {
        this.authorization = authorization;
        this.orders = orders;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public DistributorReportResponse report(Authentication authentication, LocalDate requestedFrom,
        LocalDate requestedTo, String requestedMetric) {
        UUID userId = userId(authentication);
        Set<UUID> businessIds = authorization.activeOrderDeskBusinessIds(userId);
        if (businessIds.size() != 1) throw new AccessDeniedException("Select a single authorized distributor workspace.");
        UUID businessId = businessIds.iterator().next();
        authorization.requireOrderDeskAccess(userId, businessId);

        LocalDate today = LocalDate.now(REPORT_ZONE);
        LocalDate to = requestedTo == null ? today : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        if (from.isAfter(to)) throw new IllegalArgumentException("Start date must be on or before end date.");
        long days = Duration.between(from.atStartOfDay(REPORT_ZONE), to.plusDays(1).atStartOfDay(REPORT_ZONE)).toDays();
        if (days > MAX_RANGE_DAYS) throw new IllegalArgumentException("Date range cannot exceed 366 days.");
        if (to.isAfter(today)) throw new IllegalArgumentException("End date cannot be in the future.");
        String metric = requestedMetric == null || requestedMetric.isBlank() ? "quantity" : requestedMetric.trim().toLowerCase(Locale.ROOT);
        if (!metric.equals("quantity") && !metric.equals("value")) throw new IllegalArgumentException("Top product metric must be quantity or value.");

        Instant fromTime = from.atStartOfDay(REPORT_ZONE).toInstant();
        Instant untilTime = to.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();
        List<DistributorReportResponse.DailyOrderActivity> activity = orders.dailyActivity(businessId, fromTime, untilTime).stream()
            .map(row -> new DistributorReportResponse.DailyOrderActivity(row.getDay(), row.getCurrency(), row.getOrderCount(), row.getOrderValue())).toList();
        List<ProductOrderAggregate> rankedRows = metric.equals("value")
            ? orders.topProductsByValue(businessId, fromTime, untilTime)
            : orders.topProductsByQuantity(businessId, fromTime, untilTime);
        List<DistributorReportResponse.ProductRanking> ranked = new ArrayList<>();
        for (int i = 0; i < rankedRows.size(); i++) {
            ProductOrderAggregate row = rankedRows.get(i);
            ranked.add(new DistributorReportResponse.ProductRanking(row.getProductName(), row.getCurrency(), row.getOrderedCases(), row.getOrderValue(), i + 1));
        }

        Map<OrderStatus, Long> statusCounts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) statusCounts.put(status, 0L);
        for (StatusOrderAggregate row : orders.statusSummary(businessId, fromTime, untilTime)) statusCounts.put(row.getStatus(), row.getOrderCount());
        List<DistributorReportResponse.OrderStatusTotal> statuses = Arrays.stream(OrderStatus.values())
            .map(status -> new DistributorReportResponse.OrderStatusTotal(status, statusCounts.get(status))).toList();

        List<DistributorReportResponse.MovementTypeTotal> byType = new ArrayList<>();
        Map<InventoryMovementType, MovementAggregate> movementRows = movements.summarize(businessId, fromTime, untilTime).stream()
            .collect(Collectors.toMap(MovementAggregate::getMovementType, row -> row));
        long physicalIn = 0, physicalOut = 0, reserved = 0, released = 0, delivered = 0;
        for (InventoryMovementType type : InventoryMovementType.values()) {
            MovementAggregate row = movementRows.get(type);
            long count = row == null ? 0 : row.getMovementCount();
            long onHand = row == null ? 0 : row.getOnHandDeltaCases();
            long reservedDelta = row == null ? 0 : row.getReservedDeltaCases();
            byType.add(new DistributorReportResponse.MovementTypeTotal(type, count, onHand, reservedDelta));
            physicalIn += Math.max(onHand, 0);
            physicalOut += Math.abs(Math.min(onHand, 0));
            if (type == InventoryMovementType.ORDER_RESERVATION) reserved += Math.max(reservedDelta, 0);
            if (type == InventoryMovementType.RESERVATION_RELEASE) released += Math.abs(Math.min(reservedDelta, 0));
            if (type == InventoryMovementType.DELIVERY_CONSUMPTION) delivered += Math.abs(Math.min(reservedDelta, 0));
        }
        var movementSummary = new DistributorReportResponse.InventoryMovementSummary(physicalIn, physicalOut, reserved, released, delivered, List.copyOf(byType));
        return new DistributorReportResponse(from, to, "UTC", ACTIVITY_RULE, metric, activity, List.copyOf(ranked), statuses, movementSummary, MOVEMENT_COVERAGE_RULE);
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UUID id))
            throw new AccessDeniedException("Distributor report access denied.");
        return id;
    }
}
