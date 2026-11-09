package com.vyapaaros.order.entity;

import com.vyapaaros.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", length = 30)
    private OrderStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, length = 30)
    private OrderStatus newStatus;
    @Enumerated(EnumType.STRING) @Column(name = "history_type", nullable = false, length = 24)
    private OrderHistoryEventType eventType = OrderHistoryEventType.ORDER_STATUS;
    @Enumerated(EnumType.STRING) @Column(name = "previous_fulfillment_status", length = 24)
    private FulfillmentStatus previousFulfillmentStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_fulfillment_status", length = 24)
    private FulfillmentStatus newFulfillmentStatus;
    @Column(name = "tracking_reference", length = 100)
    private String trackingReference;
    @Column(name = "request_id")
    private UUID requestId;
    @Column(name = "action_request_status", length = 16)
    private String requestStatus;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;
    @Column(length = 1000) private String reason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    public OrderStatusHistory() {}
    public OrderHistoryEventType getEventType(){return eventType;} public FulfillmentStatus getPreviousFulfillmentStatus(){return previousFulfillmentStatus;}
    public FulfillmentStatus getNewFulfillmentStatus(){return newFulfillmentStatus;} public String getTrackingReference(){return trackingReference;}
    public UUID getRequestId(){return requestId;} public String getRequestStatus(){return requestStatus;}
    public UUID getId(){return id;} public Order getOrder(){return order;} public OrderStatus getPreviousStatus(){return previousStatus;}
    public OrderStatus getNewStatus(){return newStatus;} public User getChangedBy(){return changedBy;} public String getReason(){return reason;} public Instant getCreatedAt(){return createdAt;}
    public void setOrder(Order v){order=v;} public void setPreviousStatus(OrderStatus v){previousStatus=v;} public void setNewStatus(OrderStatus v){newStatus=v;}
    public void setChangedBy(User v){changedBy=v;} public void setReason(String v){reason=v;} public void setCreatedAt(Instant v){createdAt=v;}
    public void setEventType(OrderHistoryEventType v){eventType=v;} public void setPreviousFulfillmentStatus(FulfillmentStatus v){previousFulfillmentStatus=v;}
    public void setNewFulfillmentStatus(FulfillmentStatus v){newFulfillmentStatus=v;} public void setTrackingReference(String v){trackingReference=v;}
    public void setRequestId(UUID v){requestId=v;} public void setRequestStatus(String v){requestStatus=v;}
}
