package com.vyapaaros.reports.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vyapaaros.reports.dto.DistributorReportResponse;
import com.vyapaaros.reports.service.DistributorReportCsvService;
import com.vyapaaros.reports.service.DistributorReportService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;

class DistributorReportControllerTest {
    @Test void exportReusesReportFiltersAndReturnsCsvAttachment() {
        DistributorReportService reports = mock(DistributorReportService.class);
        var controller = new DistributorReportController(reports, new DistributorReportCsvService());
        Authentication auth = mock(Authentication.class);
        LocalDate from = LocalDate.of(2026, 3, 1), to = LocalDate.of(2026, 3, 10);
        when(reports.report(auth, from, to, "value")).thenReturn(new DistributorReportResponse(from, to, "UTC", "rule", "value",
            List.of(), List.of(), List.of(), new DistributorReportResponse.InventoryMovementSummary(0,0,0,0,0,List.of()), "coverage"));

        var response = controller.export(from, to, "value", "activity", auth);

        verify(reports).report(auth, from, to, "value");
        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"vyapaaros-report-activity-2026-03-01-to-2026-03-10.csv\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertTrue(new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8).startsWith("from_date,to_date"));
    }
}
