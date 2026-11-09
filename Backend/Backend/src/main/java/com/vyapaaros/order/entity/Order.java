package com.vyapaaros.order.entity;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="orders",uniqueConstraints=@UniqueConstraint(columnNames={"retailer_id","client_order_id"}))
public class Order {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="order_number",nullable=false,unique=true,length=30) private String orderNumber;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="retailer_id",nullable=false) private Retailer retailer;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="distributor_id",nullable=false,updatable=false) private DistributorBusiness distributor;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="created_by",nullable=false) private User createdBy;
 @Column(name="client_order_id",nullable=false) private UUID clientOrderId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private OrderStatus status;
 @Enumerated(EnumType.STRING) @Column(name="fulfillment_status",nullable=false,length=24) private FulfillmentStatus fulfillmentStatus=FulfillmentStatus.NOT_STARTED;
 @Column(name="tracking_reference",length=100) private String trackingReference;
 @Column(name="fulfillment_started_at") private Instant fulfillmentStartedAt;
 @Column(name="shipped_at") private Instant shippedAt;
 @Column(name="delivered_at") private Instant deliveredAt;
 @Column(name="base_amount",nullable=false,precision=14,scale=2) private BigDecimal baseAmount;
 @Column(name="discount_amount",nullable=false,precision=14,scale=2) private BigDecimal discountAmount;
 @Column(name="final_amount",nullable=false,precision=14,scale=2) private BigDecimal finalAmount;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="credit_hold_reason",length=500) private String creditHoldReason;
 @Column(length=1000) private String notes;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @OrderBy("position ASC")
 @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true) private List<OrderItem> items=new ArrayList<>();
 public Order(){} public UUID getId(){return id;} public String getOrderNumber(){return orderNumber;} public Retailer getRetailer(){return retailer;} public DistributorBusiness getDistributor(){return distributor;}
 public User getCreatedBy(){return createdBy;} public UUID getClientOrderId(){return clientOrderId;} public OrderStatus getStatus(){return status;}
 public FulfillmentStatus getFulfillmentStatus(){return fulfillmentStatus;} public String getTrackingReference(){return trackingReference;}
 public Instant getFulfillmentStartedAt(){return fulfillmentStartedAt;} public Instant getShippedAt(){return shippedAt;} public Instant getDeliveredAt(){return deliveredAt;}
 public BigDecimal getBaseAmount(){return baseAmount;} public BigDecimal getDiscountAmount(){return discountAmount;} public BigDecimal getFinalAmount(){return finalAmount;}
 public String getCurrency(){return currency;} public String getCreditHoldReason(){return creditHoldReason;} public String getNotes(){return notes;}
 public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public List<OrderItem> getItems(){return items;}
 public void setId(UUID v){id=v;} public void setOrderNumber(String v){orderNumber=v;} public void setRetailer(Retailer v){retailer=v;} public void setDistributor(DistributorBusiness v){distributor=v;} public void setCreatedBy(User v){createdBy=v;}
 public void setClientOrderId(UUID v){clientOrderId=v;} public void setStatus(OrderStatus v){status=v;} public void setBaseAmount(BigDecimal v){baseAmount=v;}
 public void setFulfillmentStatus(FulfillmentStatus v){fulfillmentStatus=v;} public void setTrackingReference(String v){trackingReference=v;}
 public void setFulfillmentStartedAt(Instant v){fulfillmentStartedAt=v;} public void setShippedAt(Instant v){shippedAt=v;} public void setDeliveredAt(Instant v){deliveredAt=v;}
 public void setDiscountAmount(BigDecimal v){discountAmount=v;} public void setFinalAmount(BigDecimal v){finalAmount=v;} public void setCurrency(String v){currency=v;}
 public void setCreditHoldReason(String v){creditHoldReason=v;} public void setNotes(String v){notes=v;} public void setCreatedAt(Instant v){createdAt=v;}
 public void setUpdatedAt(Instant v){updatedAt=v;} public void addItem(OrderItem v){items.add(v);v.setOrder(this);}
}
