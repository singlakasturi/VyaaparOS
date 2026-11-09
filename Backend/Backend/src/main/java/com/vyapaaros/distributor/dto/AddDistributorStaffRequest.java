package com.vyapaaros.distributor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddDistributorStaffRequest(
    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid account email.")
    @Size(max = 320, message = "Email must be 320 characters or fewer.")
    String email
) {}
