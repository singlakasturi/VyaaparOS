package com.vyapaaros.inventory.dto;

import java.time.Instant;
import java.util.UUID;

public record InventoryReconciliationResponse(UUID productId, String productName,
    int currentOnHand, int currentReserved, int currentAvailable,
    boolean ledgerCompleteSinceBaseline, Long ledgerOnHand, Long ledgerReserved,
    Long ledgerAvailable, Boolean discrepancyDetected, Instant coverageStart,
    String coverageNote) { }
