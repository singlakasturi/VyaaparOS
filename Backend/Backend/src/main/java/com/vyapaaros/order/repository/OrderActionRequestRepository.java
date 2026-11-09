package com.vyapaaros.order.repository;

import com.vyapaaros.order.entity.OrderActionRequest;
import com.vyapaaros.order.entity.OrderActionRequestStatus;
import com.vyapaaros.order.entity.OrderActionRequestType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderActionRequestRepository extends JpaRepository<OrderActionRequest, UUID> {
    @EntityGraph(attributePaths = {"order", "order.retailer"})
    List<OrderActionRequest> findByOrderIdAndRequestedByIdOrderByCreatedAtDesc(UUID orderId, UUID requestedById);

    boolean existsByOrderIdAndRequestTypeAndStatusIn(UUID orderId, OrderActionRequestType type,
        Collection<OrderActionRequestStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrderActionRequest r where r.id = :id")
    Optional<OrderActionRequest> findForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"order", "order.retailer", "requestedBy", "decidedBy"})
    @Query(value = "select r from OrderActionRequest r join r.order o where o.distributor.id in :distributorIds " +
        "and (:status is null or r.status = :status) and (:type is null or r.requestType = :type) " +
        "and (:orderId is null or o.id = :orderId)",
        countQuery = "select count(r) from OrderActionRequest r join r.order o where o.distributor.id in :distributorIds " +
        "and (:status is null or r.status = :status) and (:type is null or r.requestType = :type) " +
        "and (:orderId is null or o.id = :orderId)")
    Page<OrderActionRequest> findForOrderDesk(@Param("distributorIds") Collection<UUID> distributorIds,
        @Param("status") OrderActionRequestStatus status, @Param("type") OrderActionRequestType type,
        @Param("orderId") UUID orderId, Pageable pageable);
}
