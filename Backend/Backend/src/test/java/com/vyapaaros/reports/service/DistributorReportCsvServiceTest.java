package com.vyapaaros.reports.service;

import static org.junit.jupiter.api.Assertions.*;

import com.vyapaaros.inventory.entity.InventoryMovementType;
import com.vyapaaros.order.entity.OrderStatus;
import com.vyapaaros.reports.dto.DistributorReportResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DistributorReportCsvServiceTest {
    private final DistributorReportCsvService csv = new DistributorReportCsvService();
    private final LocalDate from = LocalDate.of(2026, 3, 1), to = LocalDate.of(2026, 3, 2);

    @Test void combinedExportHasStableColumnsCrLfEscapingAndFormulaProtection() {
        var report = new DistributorReportResponse(from, to, "UTC", "status rule", "quantity",
            List.of(new DistributorReportResponse.DailyOrderActivity(from, "INR", 2, new BigDecimal("42.50"))),
            List.of(new DistributorReportResponse.ProductRanking(" \t=HYPERLINK(\"x,y\")\r\nother", "INR", 5, new BigDecimal("42.50"), 1)),
            List.of(new DistributorReportResponse.OrderStatusTotal(OrderStatus.APPROVED, 2)),
            new DistributorReportResponse.InventoryMovementSummary(3, 2, 4, 1, 2, List.of(
                new DistributorReportResponse.MovementTypeTotal(InventoryMovementType.DELIVERY_CONSUMPTION, 1, -2, -2))), "coverage");

        var result = csv.export(report, "combined");
        String content = new String(result.content(), StandardCharsets.UTF_8);
        String[] lines = content.split("\\r\\n", -1);
        assertEquals("from_date,to_date,timezone,top_product_metric,section,date,currency,rank,product_name,order_status,movement_type,count,quantity_cases,order_value,on_hand_delta_cases,reserved_delta_cases,unit", lines[0]);
        assertTrue(content.endsWith("\r\n"));
        assertFalse(content.replace("\r\n", "").contains("\n"));
        assertTrue(content.contains("\"' \t=HYPERLINK(\"\"x,y\"\")\r\nother\""));
        assertTrue(content.contains("\"PHYSICAL_OUTWARD\""));
        assertTrue(content.contains("\"DELIVERY_CONSUMPTION\""));
        assertTrue(content.contains(",\"\",5,42.50,\"\",\"\",\"cases\"\r\n"));
        assertEquals("vyapaaros-report-combined-2026-03-01-to-2026-03-02.csv", result.filename());
    }

    @Test void selectedEmptyReportStillReturnsHeadersAndValidatesReportType() {
        var report = new DistributorReportResponse(from, to, "UTC", "rule", "value", List.of(), List.of(), List.of(),
            new DistributorReportResponse.InventoryMovementSummary(0,0,0,0,0,List.of()), "coverage");
        var result = csv.export(report, "activity");
        String content = new String(result.content(), StandardCharsets.UTF_8);
        assertEquals(2, content.split("\\r\\n").length, content);
        assertTrue(content.contains("\"2026-03-01\",\"2026-03-02\",\"UTC\",\"value\",\"export_metadata\""));
        assertTrue(result.filename().contains("-activity-"));
        assertThrows(IllegalArgumentException.class, () -> csv.export(report, "payments"));
    }
}
