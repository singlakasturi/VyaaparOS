package com.vyapaaros.order.entity;
import com.vyapaaros.catalog.entity.Product;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="cart_items",uniqueConstraints=@UniqueConstraint(columnNames={"cart_id","product_id"}))
public class CartItem {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="cart_id",nullable=false) private Cart cart;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(name="paid_cases",nullable=false) private int paidCases;
 @Column(name="position",nullable=false) private int position;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 public CartItem(){} public UUID getId(){return id;} public Cart getCart(){return cart;} public Product getProduct(){return product;}
 public int getPaidCases(){return paidCases;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
 public int getPosition(){return position;}
 public void setCart(Cart v){cart=v;} public void setProduct(Product v){product=v;} public void setPaidCases(int v){paidCases=v;} public void setPosition(int v){position=v;}
 public void setCreatedAt(Instant v){createdAt=v;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
