package com.vyapaaros.distributor.entity;

import com.vyapaaros.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "distributor_businesses")
public class DistributorBusiness {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DistributorBusiness() {}

    public UUID getId() { return id; }
    public String getBusinessName() { return businessName; }
    public User getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setId(UUID id) { this.id = id; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
