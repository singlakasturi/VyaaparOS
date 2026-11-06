package com.vyapaaros.catalog.entity;

import com.vyapaaros.distributor.entity.DistributorBusiness;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_categories")
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
    @jakarta.persistence.JoinColumn(name = "distributor_id", nullable = false)
    private DistributorBusiness distributor;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProductCategory() {
    }

    public UUID getId() { return id; }
    public DistributorBusiness getDistributor() { return distributor; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setId(UUID id) { this.id = id; }
    public void setDistributor(DistributorBusiness distributor) { this.distributor = distributor; }
    public void setName(String name) { this.name = name; }
    public void setSlug(String slug) { this.slug = slug; }
    public void setDescription(String description) { this.description = description; }
    public void setActive(boolean active) { this.active = active; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
