package com.vyapaaros.pricing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PriceRequest(
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price must not be negative")
    BigDecimal pricePerCase,

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "INR", message = "Currency must be INR")
    String currency,

    Boolean gstIncluded
) {
}
