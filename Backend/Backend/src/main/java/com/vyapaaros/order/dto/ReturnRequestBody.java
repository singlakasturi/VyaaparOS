package com.vyapaaros.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReturnRequestBody(
    @NotBlank(message = "A return reason is required.")
    @Size(max = 500, message = "Reason must be at most 500 characters.")
    @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "Reason cannot contain control characters.")
    String reason
) {}
