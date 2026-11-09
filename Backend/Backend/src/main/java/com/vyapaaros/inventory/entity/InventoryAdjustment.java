package com.vyapaaros.inventory.entity;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="inventory_adjustments")
public class InventoryAdjustment {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="inventory_id",nullable=false) private Inventory inventory;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="distributor_id",nullable=false) private DistributorBusiness distributor;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id",nullable=false) private User actor;
    @Column(name="quantity_delta",nullable=false) private int quantityDelta;
    @Column(name="quantity_after",nullable=false) private int quantityAfter;
    @Column(nullable=false,length=500) private String reason;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    public UUID getId(){return id;} public Inventory getInventory(){return inventory;} public Product getProduct(){return product;} public DistributorBusiness getDistributor(){return distributor;} public User getActor(){return actor;} public int getQuantityDelta(){return quantityDelta;} public int getQuantityAfter(){return quantityAfter;} public String getReason(){return reason;} public Instant getCreatedAt(){return createdAt;}
    public void setId(UUID x){id=x;} public void setInventory(Inventory x){inventory=x;} public void setProduct(Product x){product=x;} public void setDistributor(DistributorBusiness x){distributor=x;} public void setActor(User x){actor=x;} public void setQuantityDelta(int x){quantityDelta=x;} public void setQuantityAfter(int x){quantityAfter=x;} public void setReason(String x){reason=x;} public void setCreatedAt(Instant x){createdAt=x;}
}
