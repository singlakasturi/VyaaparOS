package com.vyapaaros.reports.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DistributorReportQueryIntegrationTest {
    private static final UUID SEEDED_DISTRIBUTOR = UUID.fromString("60000000-0000-0000-0000-000000000001");
    @Autowired private DistributorReportRepository reports;
    @Autowired private DistributorMovementReportRepository movements;

    @Test void aggregateQueriesExecuteAgainstPostgresWithoutLoadingOrderEntities() {
        Instant from = Instant.parse("2000-01-01T00:00:00Z");
        Instant until = Instant.parse("2100-01-01T00:00:00Z");
        assertNotNull(reports.dailyActivity(SEEDED_DISTRIBUTOR, from, until));
        assertNotNull(reports.topProductsByQuantity(SEEDED_DISTRIBUTOR, from, until));
        assertNotNull(reports.topProductsByValue(SEEDED_DISTRIBUTOR, from, until));
        assertNotNull(reports.statusSummary(SEEDED_DISTRIBUTOR, from, until));
        assertNotNull(movements.summarize(SEEDED_DISTRIBUTOR, from, until));
    }
}
