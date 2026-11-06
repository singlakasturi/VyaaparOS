package com.vyapaaros.order.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "tally_export_batch_orders")
@IdClass(TallyExportBatchOrder.Key.class)
public class TallyExportBatchOrder {
    @Id @Column(name = "batch_id") private UUID batchId;
    @Id @Column(name = "order_id") private UUID orderId;
    public TallyExportBatchOrder() {}
    public TallyExportBatchOrder(UUID batchId, UUID orderId){this.batchId=batchId;this.orderId=orderId;}
    public UUID getBatchId(){return batchId;} public UUID getOrderId(){return orderId;}
    public static class Key implements Serializable {
        private UUID batchId; private UUID orderId;
        public Key() {} public Key(UUID b, UUID o){batchId=b;orderId=o;}
        public UUID getBatchId(){return batchId;} public UUID getOrderId(){return orderId;}
        public void setBatchId(UUID v){batchId=v;} public void setOrderId(UUID v){orderId=v;}
        @Override public boolean equals(Object o){return o instanceof Key k && java.util.Objects.equals(batchId,k.batchId)&&java.util.Objects.equals(orderId,k.orderId);}
        @Override public int hashCode(){return java.util.Objects.hash(batchId,orderId);}
    }
}
