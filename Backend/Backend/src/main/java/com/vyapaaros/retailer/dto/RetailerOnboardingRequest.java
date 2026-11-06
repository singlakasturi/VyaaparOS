package com.vyapaaros.retailer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RetailerOnboardingRequest(
    @NotBlank(message = "Business name is required")
    @Size(max = 200, message = "Business name must not exceed 200 characters")
    String businessName,

    @NotBlank(message = "Contact phone is required")
    @Size(max = 30, message = "Contact phone must not exceed 30 characters")
    String contactPhone,

    @NotBlank(message = "Address is required")
    @Size(max = 500, message = "Address must not exceed 500 characters")
    String address
) {
}
