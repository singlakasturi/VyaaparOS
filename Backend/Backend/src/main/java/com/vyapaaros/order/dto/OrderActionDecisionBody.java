package com.vyapaaros.order.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrderActionDecisionBody(
    @Size(max = 500, message = "Decision comment must be at most 500 characters.")
    @Pattern(regexp = "^$|^[^\\p{Cntrl}]*$", message = "Decision comment cannot contain control characters.")
    String comment
) {}
