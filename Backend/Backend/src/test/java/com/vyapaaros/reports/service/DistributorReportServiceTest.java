package com.vyapaaros.reports.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.inventory.entity.InventoryMovementType;
import com.vyapaaros.reports.repository.*;
import com.vyapaaros.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class DistributorReportServiceTest {
    private final DistributorAuthorizationService authorization = mock(DistributorAuthorizationService.class);
    private final DistributorReportRepository orders = mock(DistributorReportRepository.class);
    private final DistributorMovementReportRepository movements = mock(DistributorMovementReportRepository.class);
    private final DistributorReportService service = new DistributorReportService(authorization, orders, movements);
    private final UUID userId = UUID.randomUUID();
    private final UUID distributorId = UUID.randomUUID();
    private final Authentication auth = mock(Authentication.class);

    @Test void buildsScopedReportWithAllExistingStatusesAndSeparatedMovementSemantics() {
        when(auth.isAuthenticated()).thenReturn(true); when(auth.getPrincipal()).thenReturn(userId);
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(distributorId));
        when(orders.dailyActivity(eq(distributorId), any(), any())).thenReturn(List.of());
        when(orders.topProductsByQuantity(eq(distributorId), any(), any())).thenReturn(List.of());
        StatusOrderAggregate approved = status(OrderStatus.APPROVED, 3);
        when(orders.statusSummary(eq(distributorId), any(), any())).thenReturn(List.of(approved));
        MovementAggregate manual = movement(InventoryMovementType.MANUAL_ADJUSTMENT, 2, 7, 0);
        MovementAggregate reservation = movement(InventoryMovementType.ORDER_RESERVATION, 4, 0, 12);
        MovementAggregate release = movement(InventoryMovementType.RESERVATION_RELEASE, 1, 0, -3);
        MovementAggregate delivery = movement(InventoryMovementType.DELIVERY_CONSUMPTION, 2, -5, -5);
        when(movements.summarize(eq(distributorId), any(), any())).thenReturn(List.of(manual,reservation,release,delivery));
        LocalDate from = LocalDate.of(2026, 1, 31), to = LocalDate.of(2026, 2, 1);

        var result = service.report(auth, from, to, "quantity");

        assertEquals(from, result.fromDate()); assertEquals(to, result.toDate()); assertEquals("UTC", result.timezone());
        assertEquals(OrderStatus.values().length, result.orderStatuses().size());
        assertEquals(3, result.orderStatuses().stream().filter(x -> x.status() == OrderStatus.APPROVED).findFirst().orElseThrow().orderCount());
        assertEquals(0, result.orderStatuses().stream().filter(x -> x.status() == OrderStatus.CANCELLED).findFirst().orElseThrow().orderCount());
        assertEquals(7, result.inventoryMovements().physicalInwardCases());
        assertEquals(5, result.inventoryMovements().physicalOutwardCases());
        assertEquals(12, result.inventoryMovements().reservationCreatedCases());
        assertEquals(3, result.inventoryMovements().reservationReleasedCases());
        assertEquals(5, result.inventoryMovements().deliveryConsumedCases());
        assertEquals(InventoryMovementType.values().length, result.inventoryMovements().byMovementType().size());
        verify(authorization).requireOrderDeskAccess(userId, distributorId);
        ArgumentCaptor<Instant> start=ArgumentCaptor.forClass(Instant.class), end=ArgumentCaptor.forClass(Instant.class);
        verify(orders).dailyActivity(eq(distributorId), start.capture(), end.capture());
        assertEquals(Instant.parse("2026-01-31T00:00:00Z"), start.getValue());
        assertEquals(Instant.parse("2026-02-02T00:00:00Z"), end.getValue());
        verify(orders, never()).save(any()); verify(movements, never()).save(any());
    }

    @Test void defaultsToThirtyUtcDaysAndRanksProductsUsingRequestedMetric() {
        when(auth.isAuthenticated()).thenReturn(true); when(auth.getPrincipal()).thenReturn(userId);
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(distributorId));
        when(orders.dailyActivity(eq(distributorId), any(), any())).thenReturn(List.of());
        when(orders.statusSummary(eq(distributorId), any(), any())).thenReturn(List.of());
        when(movements.summarize(eq(distributorId), any(), any())).thenReturn(List.of());
        ProductOrderAggregate product = mock(ProductOrderAggregate.class);
        when(product.getProductName()).thenReturn("Tea"); when(product.getCurrency()).thenReturn("INR");
        when(product.getOrderedCases()).thenReturn(8L); when(product.getOrderValue()).thenReturn(new BigDecimal("2400.00"));
        when(orders.topProductsByValue(eq(distributorId), any(), any())).thenReturn(List.of(product));

        var result = service.report(auth, null, null, "value");

        assertEquals(LocalDate.now(java.time.ZoneOffset.UTC).minusDays(29), result.fromDate());
        assertEquals(LocalDate.now(java.time.ZoneOffset.UTC), result.toDate());
        assertEquals("value", result.topProductMetric()); assertEquals("Tea", result.topProducts().getFirst().productName());
        assertEquals(1, result.topProducts().getFirst().rank());
        assertTrue(result.orderActivity().isEmpty());
        verify(orders).topProductsByValue(eq(distributorId), any(), any());
    }

    @Test void validatesDateRangeAndMetricBeforeQueries() {
        when(auth.isAuthenticated()).thenReturn(true); when(auth.getPrincipal()).thenReturn(userId);
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(distributorId));
        assertThrows(IllegalArgumentException.class, () -> service.report(auth, LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-02"), "quantity"));
        assertThrows(IllegalArgumentException.class, () -> service.report(auth, LocalDate.parse("2026-02-02"), LocalDate.parse("2026-02-01"), "quantity"));
        assertThrows(IllegalArgumentException.class, () -> service.report(auth, LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-01"), "revenue"));
        verifyNoInteractions(orders, movements);
    }

    @Test void deniesMissingAuthenticationAndAmbiguousOrInactiveDistributorScope() {
        assertThrows(AccessDeniedException.class, () -> service.report(null, null, null, "quantity"));
        when(auth.isAuthenticated()).thenReturn(true); when(auth.getPrincipal()).thenReturn(userId);
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(distributorId, UUID.randomUUID()));
        assertThrows(AccessDeniedException.class, () -> service.report(auth, null, null, "quantity"));
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(distributorId));
        doThrow(new AccessDeniedException("inactive")).when(authorization).requireOrderDeskAccess(userId, distributorId);
        assertThrows(AccessDeniedException.class, () -> service.report(auth, null, null, "quantity"));
        verifyNoInteractions(orders, movements);
    }

    private StatusOrderAggregate status(OrderStatus status, long count) {
        StatusOrderAggregate row = mock(StatusOrderAggregate.class); when(row.getStatus()).thenReturn(status); when(row.getOrderCount()).thenReturn(count); return row;
    }
    private MovementAggregate movement(InventoryMovementType type, long count, long onHand, long reserved) {
        MovementAggregate row = mock(MovementAggregate.class); when(row.getMovementType()).thenReturn(type);
        when(row.getMovementCount()).thenReturn(count); when(row.getOnHandDeltaCases()).thenReturn(onHand); when(row.getReservedDeltaCases()).thenReturn(reserved); return row;
    }
}
