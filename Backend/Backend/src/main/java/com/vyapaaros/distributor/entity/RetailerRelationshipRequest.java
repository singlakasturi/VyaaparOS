package com.vyapaaros.distributor.entity;

import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retailer_relationship_requests")
public class RetailerRelationshipRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "retailer_id", nullable = false) private Retailer retailer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "distributor_id", nullable = false) private DistributorBusiness distributor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requested_by", nullable = false) private User requestedBy;
    @Enumerated(EnumType.STRING) @Column(name = "request_type", nullable = false, length = 16) private RequestType requestType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private RequestStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "decided_at") private Instant decidedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "decided_by") private User decidedBy;
    public enum RequestType { JOIN, SWITCH }
    public enum RequestStatus { PENDING, APPROVED, REJECTED, CANCELLED }
    public UUID getId(){return id;} public Retailer getRetailer(){return retailer;} public DistributorBusiness getDistributor(){return distributor;} public User getRequestedBy(){return requestedBy;} public RequestType getRequestType(){return requestType;} public RequestStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;} public Instant getDecidedAt(){return decidedAt;} public User getDecidedBy(){return decidedBy;}
    public void setRetailer(Retailer x){retailer=x;} public void setDistributor(DistributorBusiness x){distributor=x;} public void setRequestedBy(User x){requestedBy=x;} public void setRequestType(RequestType x){requestType=x;} public void setStatus(RequestStatus x){status=x;} public void setCreatedAt(Instant x){createdAt=x;} public void setDecidedAt(Instant x){decidedAt=x;} public void setDecidedBy(User x){decidedBy=x;}
}
