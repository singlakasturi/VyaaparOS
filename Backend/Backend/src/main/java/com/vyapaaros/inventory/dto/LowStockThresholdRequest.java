package com.vyapaaros.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LowStockThresholdRequest(@NotNull @Min(0) Integer lowStockThreshold) { }
