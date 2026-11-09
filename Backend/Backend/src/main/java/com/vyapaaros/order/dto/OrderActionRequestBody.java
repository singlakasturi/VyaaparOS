package com.vyapaaros.order.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrderActionRequestBody(
    @Size(max = 500, message = "Text must be at most 500 characters.")
    @Pattern(regexp = "^$|^[^\\p{Cntrl}]*$", message = "Text cannot contain control characters.")
    String reason
) {}
