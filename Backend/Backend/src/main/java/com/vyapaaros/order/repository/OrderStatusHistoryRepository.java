package com.vyapaaros.order.repository;

import com.vyapaaros.order.entity.OrderStatusHistory;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
