package com.vyapaaros.ai.dto;
import java.util.UUID;
import java.math.BigDecimal;
public record AiCandidateResponse(UUID id, String sku, String name, boolean priceAvailable,
    BigDecimal pricePerCase, String currency, int availableCases, boolean schemeAvailable, String schemeName) {
    public AiCandidateResponse(UUID id, String sku, String name) {
        this(id, sku, name, false, null, null, 0, false, null);
    }
}
