package com.vyapaaros.order.service;

import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.inventory.service.InventoryMovementService;
import com.vyapaaros.order.entity.Order;
import com.vyapaaros.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryRestorationService {
    private final InventoryRepository inventory;
    private final InventoryMovementService movements;
    public InventoryRestorationService(InventoryRepository inventory) { this(inventory,null); }
    @Autowired
    public InventoryRestorationService(InventoryRepository inventory,InventoryMovementService movements) { this.inventory = inventory;this.movements=movements; }
    @Transactional
    public void restore(Order order) {
        restore(order,null,"Reserved stock restored.");
    }
    @Transactional
    public void restore(Order order,User actor,String reason) {
        order.getItems().forEach(item -> {
            if (order.getDistributor() == null || item.getProduct().getDistributor() == null
                || !order.getDistributor().getId().equals(item.getProduct().getDistributor().getId())
                || inventory.restore(item.getProduct().getId(), order.getDistributor().getId(), item.getTotalCases()) != 1) {
                throw new IllegalStateException("Inventory restoration failed.");
            }
        });
        if(movements!=null) movements.reservationRelease(order,actor,reason);
    }

    @Transactional
    public void consume(Order order) {
        consume(order,null);
    }
    @Transactional
    public void consume(Order order,User actor) {
        order.getItems().forEach(item -> {
            if (order.getDistributor() == null || item.getProduct().getDistributor() == null
                || !order.getDistributor().getId().equals(item.getProduct().getDistributor().getId())
                || inventory.consumeReserved(item.getProduct().getId(), order.getDistributor().getId(), item.getTotalCases()) != 1) {
                throw new IllegalStateException("Reserved inventory consumption failed.");
            }
        });
        if(movements!=null) movements.deliveryConsumption(order,actor);
    }
}
