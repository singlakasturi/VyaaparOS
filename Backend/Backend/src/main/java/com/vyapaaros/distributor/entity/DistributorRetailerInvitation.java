package com.vyapaaros.distributor.entity;

import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="distributor_retailer_invitations")
public class DistributorRetailerInvitation {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="distributor_id",nullable=false) private DistributorBusiness distributor;
    @Column(name="invited_email",nullable=false,length=320) private String invitedEmail;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="token_hash",nullable=false,unique=true,length=64) private String tokenHash;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="created_by",nullable=false) private User createdBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
    @Column(name="accepted_at") private Instant acceptedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="accepted_by") private User acceptedBy;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="accepted_retailer_id") private Retailer acceptedRetailer;
    public UUID getId(){return id;} public DistributorBusiness getDistributor(){return distributor;} public String getInvitedEmail(){return invitedEmail;} public String getTokenHash(){return tokenHash;} public User getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;} public Instant getExpiresAt(){return expiresAt;} public Instant getRevokedAt(){return revokedAt;} public Instant getAcceptedAt(){return acceptedAt;} public User getAcceptedBy(){return acceptedBy;} public Retailer getAcceptedRetailer(){return acceptedRetailer;}
    public void setDistributor(DistributorBusiness x){distributor=x;} public void setInvitedEmail(String x){invitedEmail=x;} public void setTokenHash(String x){tokenHash=x;} public void setCreatedBy(User x){createdBy=x;} public void setCreatedAt(Instant x){createdAt=x;} public void setExpiresAt(Instant x){expiresAt=x;} public void setRevokedAt(Instant x){revokedAt=x;} public void setAcceptedAt(Instant x){acceptedAt=x;} public void setAcceptedBy(User x){acceptedBy=x;} public void setAcceptedRetailer(Retailer x){acceptedRetailer=x;}
}
