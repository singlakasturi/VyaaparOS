package com.vyapaaros.common.exception;

import java.util.List;

public class InsufficientStockException extends RuntimeException {
    private final List<StockIssue> items;

    public InsufficientStockException(List<StockIssue> items) {
        super(items.stream().map(issue -> issue.sku() + " (" + issue.productName() + "): requested "
            + issue.requestedCases() + ", available " + issue.availableCases() + " cases.")
            .reduce((left, right) -> left + " " + right)
            .orElse("Insufficient stock for this order."));
        this.items = List.copyOf(items);
    }

    public List<StockIssue> items() {
        return items;
    }

    public record StockIssue(String sku, String productName, int requestedCases, int availableCases) {}
}
