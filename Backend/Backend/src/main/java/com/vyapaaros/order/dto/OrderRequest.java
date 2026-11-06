package com.vyapaaros.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record OrderRequest(
    @NotNull UUID retailerId,
    @NotNull UUID clientOrderId,
    @Size(max = 1000) String notes,
    @Valid List<OrderItemRequest> items
) {
    public OrderRequest(UUID retailerId, UUID clientOrderId, String notes) {
        this(retailerId, clientOrderId, notes, null);
    }
}
