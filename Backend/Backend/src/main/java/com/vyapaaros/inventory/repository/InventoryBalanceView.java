package com.vyapaaros.inventory.repository;

public interface InventoryBalanceView {
    java.util.UUID getInventoryId();
    Integer getOnHandCases();
    Integer getReservedCases();
    Integer getAvailableCases();
}
