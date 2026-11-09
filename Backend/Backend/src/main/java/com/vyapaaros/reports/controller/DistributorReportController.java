package com.vyapaaros.reports.controller;

import com.vyapaaros.reports.dto.DistributorReportResponse;
import com.vyapaaros.reports.service.DistributorReportCsvService;
import com.vyapaaros.reports.service.DistributorReportService;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/distributor/me/reports")
public class DistributorReportController {
    private final DistributorReportService reports;
    private final DistributorReportCsvService csv;
    public DistributorReportController(DistributorReportService reports,
        DistributorReportCsvService csv) { this.reports = reports; this.csv = csv; }

    @GetMapping
    public DistributorReportResponse report(@RequestParam(required=false) LocalDate fromDate,
        @RequestParam(required=false) LocalDate toDate, @RequestParam(defaultValue="quantity") String topProductMetric,
        Authentication authentication) {
        return reports.report(authentication, fromDate, toDate, topProductMetric);
    }

    @GetMapping(value="/export", produces="text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required=false) LocalDate fromDate,
        @RequestParam(required=false) LocalDate toDate, @RequestParam(defaultValue="quantity") String topProductMetric,
        @RequestParam(defaultValue="combined") String report, Authentication authentication) {
        DistributorReportCsvService.CsvExport result = csv.export(reports.report(authentication, fromDate, toDate, topProductMetric), report);
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(result.content());
    }
}
