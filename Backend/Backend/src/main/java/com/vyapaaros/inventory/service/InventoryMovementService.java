package com.vyapaaros.inventory.service;

import com.vyapaaros.inventory.entity.*;
import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.inventory.repository.InventoryMovementRepository;
import com.vyapaaros.order.entity.Order;
import com.vyapaaros.order.entity.OrderItem;
import com.vyapaaros.user.entity.User;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryMovementService {
    private final InventoryRepository inventory;
    private final InventoryMovementRepository movements;

    public InventoryMovementService(InventoryRepository inventory, InventoryMovementRepository movements) {
        this.inventory = inventory; this.movements = movements;
    }

    @Transactional
    public void openingBalance(Inventory row, String reason) {
        InventoryMovement movement = base(row, InventoryMovementType.OPENING_BALANCE,
            "BASELINE:" + row.getId(), 0, 0, reason);
        setBalance(movement, row.getOnHandCases(), row.getReservedCases(), row.getAvailableCases());
        movements.save(movement);
    }

    @Transactional
    public void manualAdjustment(InventoryAdjustment adjustment) {
        Inventory row = adjustment.getInventory();
        String key = "ADJUSTMENT:" + adjustment.getId();
        if (movements.existsByEventKey(key)) return;
        InventoryMovement movement = base(row, InventoryMovementType.MANUAL_ADJUSTMENT, key,
            adjustment.getQuantityDelta(), 0, adjustment.getReason());
        movement.setProduct(adjustment.getProduct()); movement.setDistributor(adjustment.getDistributor());
        movement.setActor(adjustment.getActor()); movement.setReference("Adjustment " + adjustment.getId());
        setBalance(movement, row.getOnHandCases(), row.getReservedCases(), row.getAvailableCases());
        movements.save(movement);
    }

    @Transactional
    public void orderReservation(Order order, User actor) {
        order.getItems().forEach(item -> recordOrderMovement(order, item, actor,
            InventoryMovementType.ORDER_RESERVATION, 0, item.getTotalCases(),
            "ORDER:" + order.getId() + ":RESERVATION:", "Order stock reserved."));
    }

    @Transactional
    public void reservationRelease(Order order, User actor, String reason) {
        order.getItems().forEach(item -> recordOrderMovement(order, item, actor,
            InventoryMovementType.RESERVATION_RELEASE, 0, -item.getTotalCases(),
            "ORDER:" + order.getId() + ":RELEASE:", reason));
    }

    @Transactional
    public void deliveryConsumption(Order order, User actor) {
        order.getItems().forEach(item -> recordOrderMovement(order, item, actor,
            InventoryMovementType.DELIVERY_CONSUMPTION, -item.getTotalCases(), -item.getTotalCases(),
            "ORDER:" + order.getId() + ":DELIVERY:", "Delivered order stock consumed."));
    }

    private void recordOrderMovement(Order order, OrderItem item, User actor, InventoryMovementType type,
        int onHandDelta, int reservedDelta, String keyPrefix, String reason) {
        String key = keyPrefix + item.getProduct().getId();
        if (movements.existsByEventKey(key)) return;
        var balance = inventory.findBalanceByProductIdAndDistributorId(item.getProduct().getId(), order.getDistributor().getId())
            .orElseThrow(() -> new IllegalStateException("Inventory movement balance was not found."));
        InventoryMovement movement = new InventoryMovement();
        movement.setInventory(inventory.getReferenceById(balance.getInventoryId()));
        movement.setProduct(item.getProduct()); movement.setDistributor(order.getDistributor()); movement.setOrder(order);
        movement.setActor(actor); movement.setMovementType(type); movement.setOnHandDelta(onHandDelta);
        movement.setReservedDelta(reservedDelta); movement.setEventKey(key); movement.setReference(order.getOrderNumber());
        movement.setReason(reason); movement.setCreatedAt(Instant.now());
        setBalance(movement, balance.getOnHandCases(), balance.getReservedCases(), balance.getAvailableCases());
        movements.save(movement);
    }

    private InventoryMovement base(Inventory row, InventoryMovementType type, String key,
        int onHandDelta, int reservedDelta, String reason) {
        InventoryMovement movement = new InventoryMovement(); movement.setInventory(row);
        movement.setProduct(row.getProduct()); movement.setDistributor(row.getDistributor());
        movement.setMovementType(type); movement.setEventKey(key); movement.setOnHandDelta(onHandDelta);
        movement.setReservedDelta(reservedDelta); movement.setReason(reason); movement.setCreatedAt(Instant.now());
        return movement;
    }

    private void setBalance(InventoryMovement movement, int onHand, int reserved, int available) {
        movement.setOnHandAfter(onHand); movement.setReservedAfter(reserved); movement.setAvailableAfter(available);
    }
}
