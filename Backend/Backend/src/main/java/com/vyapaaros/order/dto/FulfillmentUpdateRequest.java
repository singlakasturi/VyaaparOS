package com.vyapaaros.order.dto;

import com.vyapaaros.order.entity.FulfillmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FulfillmentUpdateRequest(
    @NotNull FulfillmentStatus fulfillmentStatus,
    @Size(max = 100)
    @Pattern(regexp = "^$|^[\\p{L}\\p{N}][\\p{L}\\p{N} ._/#-]{0,99}$", message = "Tracking reference contains unsupported characters.")
    String trackingReference
) {}
