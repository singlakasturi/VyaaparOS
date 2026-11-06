package com.vyapaaros.order.entity;
import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.schemes.entity.SchemeType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="order_items")
public class OrderItem {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private Order order;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
 @Column(name="product_sku",nullable=false,length=60) private String productSku;
 @Column(name="product_name",nullable=false,length=200) private String productName;
 @Column(name="paid_cases",nullable=false) private int paidCases;
 @Column(name="position",nullable=false) private int position;
 @Column(name="free_cases",nullable=false) private int freeCases;
 @Column(name="total_cases",nullable=false) private int totalCases;
 @Column(name="unit_price_per_case",nullable=false,precision=12,scale=2) private BigDecimal unitPricePerCase;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="gst_included") private Boolean gstIncluded;
 @Column(name="base_amount",nullable=false,precision=14,scale=2) private BigDecimal baseAmount;
 @Column(name="discount_amount",nullable=false,precision=14,scale=2) private BigDecimal discountAmount;
 @Column(name="final_amount",nullable=false,precision=14,scale=2) private BigDecimal finalAmount;
 @Column(name="applied_scheme_id") private UUID appliedSchemeId;
 @Column(name="scheme_name",length=150) private String schemeName;
 @Column(name="scheme_type",length=30) private String schemeType;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 public OrderItem(){} public UUID getId(){return id;} public Order getOrder(){return order;} public Product getProduct(){return product;}
 public String getProductSku(){return productSku;} public String getProductName(){return productName;} public int getPaidCases(){return paidCases;} public int getFreeCases(){return freeCases;} public int getTotalCases(){return totalCases;}
 public int getPosition(){return position;}
 public BigDecimal getUnitPricePerCase(){return unitPricePerCase;} public String getCurrency(){return currency;} public Boolean getGstIncluded(){return gstIncluded;} public BigDecimal getBaseAmount(){return baseAmount;} public BigDecimal getDiscountAmount(){return discountAmount;} public BigDecimal getFinalAmount(){return finalAmount;} public UUID getAppliedSchemeId(){return appliedSchemeId;} public String getSchemeName(){return schemeName;} public String getSchemeType(){return schemeType;} public Instant getCreatedAt(){return createdAt;}
 public void setOrder(Order v){order=v;} public void setProduct(Product v){product=v;} public void setProductSku(String v){productSku=v;} public void setProductName(String v){productName=v;} public void setPaidCases(int v){paidCases=v;} public void setPosition(int v){position=v;} public void setFreeCases(int v){freeCases=v;} public void setTotalCases(int v){totalCases=v;} public void setUnitPricePerCase(BigDecimal v){unitPricePerCase=v;} public void setCurrency(String v){currency=v;} public void setGstIncluded(Boolean v){gstIncluded=v;} public void setBaseAmount(BigDecimal v){baseAmount=v;} public void setDiscountAmount(BigDecimal v){discountAmount=v;} public void setFinalAmount(BigDecimal v){finalAmount=v;} public void setAppliedSchemeId(UUID v){appliedSchemeId=v;} public void setSchemeName(String v){schemeName=v;} public void setSchemeType(String v){schemeType=v;} public void setCreatedAt(Instant v){createdAt=v;}
}
