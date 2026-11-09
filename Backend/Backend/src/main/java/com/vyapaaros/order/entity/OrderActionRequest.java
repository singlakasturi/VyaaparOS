package com.vyapaaros.order.entity;

import com.vyapaaros.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_action_requests")
public class OrderActionRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false) private Order order;
    @Enumerated(EnumType.STRING) @Column(name = "request_type", nullable = false, length = 20) private OrderActionRequestType requestType;
    @Enumerated(EnumType.STRING) @Column(name = "request_status", nullable = false, length = 16) private OrderActionRequestStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requested_by", nullable = false) private User requestedBy;
    @Column(length = 500) private String reason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "decided_by") private User decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_comment", length = 500) private String decisionComment;

    public OrderActionRequest() {}
    public UUID getId(){return id;} public Order getOrder(){return order;} public OrderActionRequestType getRequestType(){return requestType;}
    public OrderActionRequestStatus getStatus(){return status;} public User getRequestedBy(){return requestedBy;} public String getReason(){return reason;}
    public Instant getCreatedAt(){return createdAt;} public User getDecidedBy(){return decidedBy;} public Instant getDecidedAt(){return decidedAt;}
    public String getDecisionComment(){return decisionComment;}
    public void setOrder(Order value){order=value;} public void setRequestType(OrderActionRequestType value){requestType=value;}
    public void setStatus(OrderActionRequestStatus value){status=value;} public void setRequestedBy(User value){requestedBy=value;}
    public void setReason(String value){reason=value;} public void setCreatedAt(Instant value){createdAt=value;}
    public void setDecidedBy(User value){decidedBy=value;} public void setDecidedAt(Instant value){decidedAt=value;}
    public void setDecisionComment(String value){decisionComment=value;}
}
