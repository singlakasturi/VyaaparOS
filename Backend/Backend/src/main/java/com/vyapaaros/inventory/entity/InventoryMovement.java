package com.vyapaaros.inventory.entity;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.order.entity.Order;
import com.vyapaaros.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements", uniqueConstraints = @UniqueConstraint(name = "inventory_movements_event_key_uq", columnNames = "event_key"))
public class InventoryMovement {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "distributor_id", nullable = false)
    private DistributorBusiness distributor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id")
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id")
    private User actor;
    @Enumerated(EnumType.STRING) @Column(name = "movement_type", nullable = false, length = 32)
    private InventoryMovementType movementType;
    @Column(name = "on_hand_delta", nullable = false) private int onHandDelta;
    @Column(name = "reserved_delta", nullable = false) private int reservedDelta;
    @Column(name = "on_hand_after") private Integer onHandAfter;
    @Column(name = "reserved_after") private Integer reservedAfter;
    @Column(name = "available_after") private Integer availableAfter;
    @Column(name = "event_key", nullable = false, length = 200) private String eventKey;
    @Column(name = "reference", length = 200) private String reference;
    @Column(name = "reason", length = 500) private String reason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public UUID getId() { return id; }
    public Inventory getInventory() { return inventory; }
    public Product getProduct() { return product; }
    public DistributorBusiness getDistributor() { return distributor; }
    public Order getOrder() { return order; }
    public User getActor() { return actor; }
    public InventoryMovementType getMovementType() { return movementType; }
    public int getOnHandDelta() { return onHandDelta; }
    public int getReservedDelta() { return reservedDelta; }
    public Integer getOnHandAfter() { return onHandAfter; }
    public Integer getReservedAfter() { return reservedAfter; }
    public Integer getAvailableAfter() { return availableAfter; }
    public String getEventKey() { return eventKey; }
    public String getReference() { return reference; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setInventory(Inventory value) { inventory = value; }
    public void setProduct(Product value) { product = value; }
    public void setDistributor(DistributorBusiness value) { distributor = value; }
    public void setOrder(Order value) { order = value; }
    public void setActor(User value) { actor = value; }
    public void setMovementType(InventoryMovementType value) { movementType = value; }
    public void setOnHandDelta(int value) { onHandDelta = value; }
    public void setReservedDelta(int value) { reservedDelta = value; }
    public void setOnHandAfter(Integer value) { onHandAfter = value; }
    public void setReservedAfter(Integer value) { reservedAfter = value; }
    public void setAvailableAfter(Integer value) { availableAfter = value; }
    public void setEventKey(String value) { eventKey = value; }
    public void setReference(String value) { reference = value; }
    public void setReason(String value) { reason = value; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
