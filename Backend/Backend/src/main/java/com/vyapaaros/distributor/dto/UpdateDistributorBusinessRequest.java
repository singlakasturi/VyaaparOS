package com.vyapaaros.distributor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDistributorBusinessRequest(
    @NotBlank(message = "Business name is required.")
    @Size(max = 200, message = "Business name must be 200 characters or fewer.")
    String businessName
) {}
