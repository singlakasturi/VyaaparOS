package com.vyapaaros.pricing.entity;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.retailer.entity.Retailer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_prices")
public class ProductPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distributor_id", nullable = false)
    private DistributorBusiness distributor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retailer_id")
    private Retailer retailer;

    @Column(name = "price_per_case", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerCase;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "gst_included")
    private Boolean gstIncluded;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProductPrice() {
    }

    public UUID getId() { return id; }
    public DistributorBusiness getDistributor() { return distributor; }
    public Product getProduct() { return product; }
    public Retailer getRetailer() { return retailer; }
    public BigDecimal getPricePerCase() { return pricePerCase; }
    public String getCurrency() { return currency; }
    public Boolean getGstIncluded() { return gstIncluded; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setId(UUID id) { this.id = id; }
    public void setDistributor(DistributorBusiness distributor) { this.distributor = distributor; }
    public void setProduct(Product product) { this.product = product; }
    public void setRetailer(Retailer retailer) { this.retailer = retailer; }
    public void setPricePerCase(BigDecimal pricePerCase) { this.pricePerCase = pricePerCase; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setGstIncluded(Boolean gstIncluded) { this.gstIncluded = gstIncluded; }
    public void setActive(boolean active) { this.active = active; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
