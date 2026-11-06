package com.vyapaaros.distributor.entity;

import com.vyapaaros.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "distributor_business_memberships")
public class DistributorMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distributor_id", nullable = false, updatable = false)
    private DistributorBusiness distributor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DistributorMembershipStatus status;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public DistributorMembership() {}

    public UUID getId() { return id; }
    public DistributorBusiness getDistributor() { return distributor; }
    public User getUser() { return user; }
    public DistributorMembershipStatus getStatus() { return status; }
    public Instant getGrantedAt() { return grantedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setId(UUID id) { this.id = id; }
    public void setDistributor(DistributorBusiness distributor) { this.distributor = distributor; }
    public void setUser(User user) { this.user = user; }
    public void setStatus(DistributorMembershipStatus status) { this.status = status; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
