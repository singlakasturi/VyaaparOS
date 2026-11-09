package com.vyapaaros.reports.service;

import com.vyapaaros.reports.dto.DistributorReportResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DistributorReportCsvService {
    private static final String[] HEADERS = {"from_date", "to_date", "timezone", "top_product_metric", "section", "date", "currency", "rank", "product_name", "order_status", "movement_type", "count", "quantity_cases", "order_value", "on_hand_delta_cases", "reserved_delta_cases", "unit"};
    private static final Set<String> REPORT_TYPES = Set.of("activity", "top_products", "order_statuses", "inventory_movements", "combined");

    public CsvExport export(DistributorReportResponse report, String requestedType) {
        String type = requestedType == null || requestedType.isBlank() ? "combined" : requestedType.trim().toLowerCase(Locale.ROOT);
        if (!REPORT_TYPES.contains(type)) throw new IllegalArgumentException("Report must be activity, top_products, order_statuses, inventory_movements, or combined.");
        StringBuilder csv = new StringBuilder(4096);
        csv.append(String.join(",", HEADERS)).append("\r\n");
        appendRow(csv, row(report, "export_metadata", null, null, null, null, null, null, null, null, null, null, null, "date range inclusive; dates are UTC"));
        if (type.equals("activity") || type.equals("combined")) {
            for (var row : report.orderActivity()) appendRow(csv, row(report, "order_activity", row.day(), row.currency(), null, null, null, null, row.orderCount(), null, row.orderValue(), null, null, row.currency()));
        }
        if (type.equals("top_products") || type.equals("combined")) {
            for (var item : report.topProducts()) appendRow(csv, row(report, "top_products", null, item.currency(), item.rank(), item.productName(), null, null, null, item.orderedCases(), item.orderValue(), null, null,
                report.topProductMetric().equals("value") ? "currency" : "cases"));
        }
        if (type.equals("order_statuses") || type.equals("combined")) {
            for (var item : report.orderStatuses()) appendRow(csv, row(report, "order_statuses", null, null, null, null, item.status().name(), null, item.orderCount(), null, null, null, null, "orders"));
        }
        if (type.equals("inventory_movements") || type.equals("combined")) {
            var summary = report.inventoryMovements();
            appendRow(csv, row(report, "inventory_summary", null, null, null, null, null, "PHYSICAL_INWARD", null, summary.physicalInwardCases(), null, null, null, "cases"));
            appendRow(csv, row(report, "inventory_summary", null, null, null, null, null, "PHYSICAL_OUTWARD", null, summary.physicalOutwardCases(), null, null, null, "cases"));
            appendRow(csv, row(report, "inventory_summary", null, null, null, null, null, "RESERVATION_CREATED", null, summary.reservationCreatedCases(), null, null, null, "cases"));
            appendRow(csv, row(report, "inventory_summary", null, null, null, null, null, "RESERVATION_RELEASED", null, summary.reservationReleasedCases(), null, null, null, "cases"));
            appendRow(csv, row(report, "inventory_summary", null, null, null, null, null, "DELIVERY_CONSUMED", null, summary.deliveryConsumedCases(), null, null, null, "cases"));
            for (var item : summary.byMovementType()) appendRow(csv, row(report, "inventory_movement_type", null, null, null, null, null, item.movementType().name(), item.movementCount(), null, null, item.onHandDeltaCases(), item.reservedDeltaCases(), "cases"));
        }
        String filename = "vyapaaros-report-" + type + "-" + report.fromDate() + "-to-" + report.toDate() + ".csv";
        return new CsvExport(filename, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Object[] row(DistributorReportResponse report, Object section, Object date, Object currency,
        Object rank, Object productName, Object orderStatus, Object movementType, Object count,
        Object quantityCases, Object orderValue, Object onHandDelta, Object reservedDelta, Object unit) {
        return new Object[] {report.fromDate(), report.toDate(), report.timezone(), report.topProductMetric(),
            section, date, currency, rank, productName, orderStatus, movementType, count,
            quantityCases, orderValue, onHandDelta, reservedDelta, unit};
    }

    private void appendRow(StringBuilder csv, Object... values) {
        for (int i = 0; i < HEADERS.length; i++) {
            if (i > 0) csv.append(',');
            Object value = i < values.length ? values[i] : null;
            if (value instanceof Number) csv.append(value);
            else csv.append('"').append(escape(safeText(value == null ? "" : value.toString()))).append('"');
        }
        csv.append("\r\n");
    }

    private String safeText(String value) {
        int index = 0;
        while (index < value.length()) {
            char c = value.charAt(index);
            int category = Character.getType(c);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c) || category == Character.FORMAT) index++;
            else break;
        }
        if (index < value.length() && "=+-@".indexOf(value.charAt(index)) >= 0) return "'" + value;
        return value;
    }

    private String escape(String value) { return value.replace("\"", "\"\""); }

    public record CsvExport(String filename, byte[] content) { }
}
