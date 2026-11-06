package com.vyapaaros.inventory.entity;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
    @jakarta.persistence.JoinColumn(name = "distributor_id", nullable = false)
    private DistributorBusiness distributor;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "available_cases", nullable = false)
    private int availableCases;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Inventory() {
    }

    public UUID getId() { return id; }
    public DistributorBusiness getDistributor() { return distributor; }
    public Product getProduct() { return product; }
    public int getAvailableCases() { return availableCases; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setId(UUID id) { this.id = id; }
    public void setDistributor(DistributorBusiness distributor) { this.distributor = distributor; }
    public void setProduct(Product product) { this.product = product; }
    public void setAvailableCases(int availableCases) { this.availableCases = availableCases; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
