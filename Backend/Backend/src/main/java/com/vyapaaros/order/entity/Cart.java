package com.vyapaaros.order.entity;

import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="carts")
public class Cart {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="retailer_id",nullable=false) private Retailer retailer;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="created_by",nullable=false) private User createdBy;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private CartStatus status;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @OrderBy("position ASC")
    @OneToMany(mappedBy="cart",cascade=CascadeType.ALL,orphanRemoval=true,fetch=FetchType.LAZY) private List<CartItem> items=new ArrayList<>();
    public Cart() {}
    public UUID getId(){return id;} public Retailer getRetailer(){return retailer;} public User getCreatedBy(){return createdBy;}
    public CartStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public List<CartItem> getItems(){return items;}
    public void setRetailer(Retailer v){retailer=v;} public void setCreatedBy(User v){createdBy=v;} public void setStatus(CartStatus v){status=v;}
    public void setCreatedAt(Instant v){createdAt=v;} public void setUpdatedAt(Instant v){updatedAt=v;}
    public void addItem(CartItem item){items.add(item);item.setCart(this);}
}
