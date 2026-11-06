package com.vyapaaros.order.dto;

import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDeskExportRequest(List<UUID> orderIds, Instant startDate, Instant endDate) {
    @AssertTrue(message = "End date must be after start date.")
    public boolean validDateRange() {
        return startDate == null || endDate == null || endDate.isAfter(startDate);
    }
}
