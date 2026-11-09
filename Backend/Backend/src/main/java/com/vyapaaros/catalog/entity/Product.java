package com.vyapaaros.catalog.entity;

import com.vyapaaros.distributor.entity.DistributorBusiness;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distributor_id", nullable = false)
    private DistributorBusiness distributor;

    @Column(name = "sku", nullable = false, length = 60)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "brand", length = 120)
    private String brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    @Column(name = "unit_name", nullable = false, length = 50)
    private String unitName;

    @Column(name = "unit_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal unitQuantity;

    @Column(name = "units_per_case", nullable = false)
    private int unitsPerCase;

    @Column(name = "case_description", length = 100)
    private String caseDescription;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Product() {
    }

    public UUID getId() { return id; }
    public DistributorBusiness getDistributor() { return distributor; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getBrand() { return brand; }
    public ProductCategory getCategory() { return category; }
    public String getUnitName() { return unitName; }
    public BigDecimal getUnitQuantity() { return unitQuantity; }
    public int getUnitsPerCase() { return unitsPerCase; }
    public String getCaseDescription() { return caseDescription; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setId(UUID id) { this.id = id; }
    public void setDistributor(DistributorBusiness distributor) { this.distributor = distributor; }
    public void setSku(String sku) { this.sku = sku; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setBrand(String brand) { this.brand = brand; }
    public void setCategory(ProductCategory category) { this.category = category; }
    public void setUnitName(String unitName) { this.unitName = unitName; }
    public void setUnitQuantity(BigDecimal unitQuantity) { this.unitQuantity = unitQuantity; }
    public void setUnitsPerCase(int unitsPerCase) { this.unitsPerCase = unitsPerCase; }
    public void setCaseDescription(String caseDescription) { this.caseDescription = caseDescription; }
    public void setActive(boolean active) { this.active = active; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
