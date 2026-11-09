package com.vyapaaros.schemes.entity;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.entity.ProductCategory;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.retailer.entity.Retailer;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_schemes")
public class ProductScheme {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "distributor_id", nullable = false)
    private DistributorBusiness distributor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id")
    private Product product;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id")
    private ProductCategory category;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "retailer_id")
    private Retailer retailer;
    @Enumerated(EnumType.STRING) @Column(name = "scheme_type", nullable = false, length = 30)
    private SchemeType schemeType;
    @Column(nullable = false, length = 150) private String name;
    @Column(length = 500) private String description;
    @Column(name = "buy_quantity") private Integer buyQuantity;
    @Column(name = "free_quantity") private Integer freeQuantity;
    @Column(name = "minimum_quantity") private Integer minimumQuantity;
    @Column(name = "discount_percentage", precision = 5, scale = 2) private BigDecimal discountPercentage;
    @Column(name = "starts_at") private Instant startsAt;
    @Column(name = "ends_at") private Instant endsAt;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    public ProductScheme() {}
    public UUID getId() { return id; } public Product getProduct() { return product; }
    public ProductCategory getCategory() { return category; }
    public DistributorBusiness getDistributor() { return distributor; }
    public Retailer getRetailer() { return retailer; } public SchemeType getSchemeType() { return schemeType; }
    public String getName() { return name; } public String getDescription() { return description; }
    public Integer getBuyQuantity() { return buyQuantity; } public Integer getFreeQuantity() { return freeQuantity; }
    public Integer getMinimumQuantity() { return minimumQuantity; } public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public Instant getStartsAt() { return startsAt; } public Instant getEndsAt() { return endsAt; }
    public boolean isActive() { return active; } public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setId(UUID v) { id = v; }
    public void setDistributor(DistributorBusiness v) { distributor = v; }
    public void setProduct(Product v) { product = v; } public void setCategory(ProductCategory v) { category = v; }
    public void setRetailer(Retailer v) { retailer = v; }
    public void setSchemeType(SchemeType v) { schemeType = v; } public void setName(String v) { name = v; }
    public void setDescription(String v) { description = v; } public void setBuyQuantity(Integer v) { buyQuantity = v; }
    public void setFreeQuantity(Integer v) { freeQuantity = v; } public void setMinimumQuantity(Integer v) { minimumQuantity = v; }
    public void setDiscountPercentage(BigDecimal v) { discountPercentage = v; } public void setStartsAt(Instant v) { startsAt = v; }
    public void setEndsAt(Instant v) { endsAt = v; } public void setActive(boolean v) { active = v; }
    public void setCreatedAt(Instant v) { createdAt = v; } public void setUpdatedAt(Instant v) { updatedAt = v; }
}
