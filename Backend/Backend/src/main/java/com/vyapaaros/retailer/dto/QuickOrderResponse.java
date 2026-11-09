package com.vyapaaros.retailer.dto;

import com.vyapaaros.order.dto.CartResponse;
import java.util.List;
import java.util.UUID;

public record QuickOrderResponse(CartResponse cart, List<RowOutcome> results) {
    public record RowOutcome(int rowNumber, String productCode, UUID productId, String productName,
        Integer quantity, String outcome, String message) { }
}
