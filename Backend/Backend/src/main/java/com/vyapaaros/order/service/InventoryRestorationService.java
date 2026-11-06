package com.vyapaaros.order.service;

import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.order.entity.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryRestorationService {
    private final InventoryRepository inventory;
    public InventoryRestorationService(InventoryRepository inventory) { this.inventory = inventory; }
    @Transactional
    public void restore(Order order) {
        order.getItems().forEach(item -> {
            if (order.getDistributor() == null || item.getProduct().getDistributor() == null
                || !order.getDistributor().getId().equals(item.getProduct().getDistributor().getId())
                || inventory.restore(item.getProduct().getId(), order.getDistributor().getId(), item.getTotalCases()) != 1) {
                throw new IllegalStateException("Inventory restoration failed.");
            }
        });
    }
}
