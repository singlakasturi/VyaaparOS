package com.vyapaaros.inventory.repository;

import com.vyapaaros.inventory.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment,UUID> {
    List<InventoryAdjustment> findTop100ByInventoryIdAndDistributorIdOrderByCreatedAtDescIdDesc(UUID inventoryId,UUID distributorId);
}
