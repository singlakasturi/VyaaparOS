package com.vyapaaros.order.entity;

import com.vyapaaros.user.entity.User;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tally_export_batches")
public class TallyExportBatch {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 255) private String filename;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "distributor_id", nullable = false)
    private DistributorBusiness distributor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private User createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "order_count", nullable = false) private int orderCount;
    public TallyExportBatch() {}
    public UUID getId(){return id;} public String getFilename(){return filename;} public User getCreatedBy(){return createdBy;}
    public DistributorBusiness getDistributor(){return distributor;}
    public Instant getCreatedAt(){return createdAt;} public int getOrderCount(){return orderCount;}
    public void setFilename(String v){filename=v;} public void setDistributor(DistributorBusiness v){distributor=v;} public void setCreatedBy(User v){createdBy=v;} public void setCreatedAt(Instant v){createdAt=v;} public void setOrderCount(int v){orderCount=v;}
}
