package com.vyapaaros.retailer.entity;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retailer_favorites", uniqueConstraints = @UniqueConstraint(
    name = "retailer_favorites_retailer_distributor_product_uq",
    columnNames = {"retailer_id", "distributor_id", "product_id"}
))
public class RetailerFavorite {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retailer_id", nullable = false, updatable = false)
    private Retailer retailer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distributor_id", nullable = false, updatable = false)
    private DistributorBusiness distributor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt;

    public RetailerFavorite() { }

    public UUID getId() { return id; }
    public Retailer getRetailer() { return retailer; }
    public DistributorBusiness getDistributor() { return distributor; }
    public Product getProduct() { return product; }
    public Instant getSavedAt() { return savedAt; }
    public void setRetailer(Retailer retailer) { this.retailer = retailer; }
    public void setDistributor(DistributorBusiness distributor) { this.distributor = distributor; }
    public void setProduct(Product product) { this.product = product; }
    public void setSavedAt(Instant savedAt) { this.savedAt = savedAt; }
}
