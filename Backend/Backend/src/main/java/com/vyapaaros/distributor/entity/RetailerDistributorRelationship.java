package com.vyapaaros.distributor.entity;

import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retailer_distributor_relationships")
public class RetailerDistributorRelationship {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retailer_id", nullable = false, updatable = false)
    private Retailer retailer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distributor_id", nullable = false, updatable = false)
    private DistributorBusiness distributor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RelationshipStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "started_by", nullable = false, updatable = false)
    private User startedBy;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public RetailerDistributorRelationship() {}

    public UUID getId() { return id; }
    public Retailer getRetailer() { return retailer; }
    public DistributorBusiness getDistributor() { return distributor; }
    public RelationshipStatus getStatus() { return status; }
    public User getStartedBy() { return startedBy; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setId(UUID id) { this.id = id; }
    public void setRetailer(Retailer retailer) { this.retailer = retailer; }
    public void setDistributor(DistributorBusiness distributor) { this.distributor = distributor; }
    public void setStatus(RelationshipStatus status) { this.status = status; }
    public void setStartedBy(User startedBy) { this.startedBy = startedBy; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public enum RelationshipStatus { ACTIVE, ENDED }
}
