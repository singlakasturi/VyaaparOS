package com.vyapaaros.retailer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record QuickOrderRequest(@NotNull @Size(min = 1, max = 100) List<QuickOrderRow> rows) {
    public record QuickOrderRow(String productCode, String quantity) { }
}
