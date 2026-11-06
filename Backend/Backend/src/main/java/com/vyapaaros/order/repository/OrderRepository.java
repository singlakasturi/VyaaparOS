package com.vyapaaros.order.repository;
import com.vyapaaros.order.entity.*;
import java.util.*;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
public interface OrderRepository extends JpaRepository<Order,UUID> {
 @Query(value="select nextval('order_number_seq')",nativeQuery=true) long nextNumber();
 Optional<Order> findByRetailerIdAndClientOrderId(UUID retailerId,UUID clientOrderId);
 @Query("select distinct o from Order o left join fetch o.items where o.id=:id")
 Optional<Order> findDetailed(@Param("id") UUID id);
 Page<Order> findByRetailerId(UUID retailerId,Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select o from Order o where o.id=:id")
 Optional<Order> findForUpdate(@Param("id") UUID id);
 @Query("select distinct o from Order o join fetch o.retailer r left join fetch o.items where (:status is null or o.status=:status) and (:retailerId is null or r.id=:retailerId) and (:startDate is null or o.createdAt>=:startDate) and (:endDate is null or o.createdAt<:endDate)")
 Page<Order> search(@Param("status") OrderStatus status,@Param("retailerId") UUID retailerId,@Param("startDate") Instant startDate,@Param("endDate") Instant endDate,Pageable pageable);
 @Query("select distinct o from Order o join fetch o.retailer r left join fetch o.items where o.distributor.id in :distributorIds and (:status is null or o.status=:status) and (:retailerId is null or r.id=:retailerId) and (:startDate is null or o.createdAt>=:startDate) and (:endDate is null or o.createdAt<:endDate)")
 Page<Order> searchByDistributorIds(@Param("distributorIds") Collection<UUID> distributorIds,@Param("status") OrderStatus status,@Param("retailerId") UUID retailerId,@Param("startDate") Instant startDate,@Param("endDate") Instant endDate,Pageable pageable);
 @Query("select distinct o from Order o join fetch o.retailer r left join fetch o.items where o.id in :ids")
 List<Order> findDetailedByIdIn(@Param("ids") Collection<UUID> ids);
}
