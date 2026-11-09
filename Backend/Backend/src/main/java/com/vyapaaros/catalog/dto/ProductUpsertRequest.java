package com.vyapaaros.catalog.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductUpsertRequest(
    @NotBlank @Size(max=60) String sku,
    @NotBlank @Size(max=200) String name,
    @Size(max=1000) String description,
    @Size(max=120) String brand,
    @NotNull UUID categoryId,
    @NotBlank @Size(max=50) String unitName,
    @NotNull @DecimalMin(value="0.001") @Digits(integer=9,fraction=3) BigDecimal unitQuantity,
    @Min(1) @Max(1000000) int unitsPerCase,
    @Size(max=100) String caseDescription
) {}
