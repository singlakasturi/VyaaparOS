package com.vyapaaros.reports.repository;

import com.vyapaaros.inventory.entity.InventoryMovementType;

public interface MovementAggregate {
    InventoryMovementType getMovementType();
    Long getMovementCount();
    Long getOnHandDeltaCases();
    Long getReservedDeltaCases();
}
