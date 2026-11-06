package com.vyapaaros.order.repository;

import com.vyapaaros.order.entity.TallyExportBatchOrder;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TallyExportBatchOrderRepository extends JpaRepository<TallyExportBatchOrder, TallyExportBatchOrder.Key> {
    boolean existsByOrderId(UUID orderId);
    List<TallyExportBatchOrder> findByBatchId(UUID batchId);
}
