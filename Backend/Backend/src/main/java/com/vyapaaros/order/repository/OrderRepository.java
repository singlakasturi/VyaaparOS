package com.vyapaaros.order.repository;
import com.vyapaaros.order.entity.*;
import com.vyapaaros.order.dto.OrderStatusCount;
import java.util.*;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
public interface OrderRepository extends JpaRepository<Order,UUID> {
 Instant EARLIEST_ORDER_DATE = Instant.parse("0001-01-01T00:00:00Z");
 Instant LATEST_ORDER_DATE = Instant.parse("9999-12-31T23:59:59Z");
 @Query(value="select nextval('order_number_seq')",nativeQuery=true) long nextNumber();
 Optional<Order> findByRetailerIdAndClientOrderId(UUID retailerId,UUID clientOrderId);
 @Query("select distinct o from Order o left join fetch o.items where o.id=:id")
 Optional<Order> findDetailed(@Param("id") UUID id);
 Page<Order> findByRetailerId(UUID retailerId,Pageable pageable);
 @Query("select distinct o from Order o left join fetch o.items where o.retailer.id=:retailerId and o.distributor.id=:distributorId and o.createdAt>=:startDate and o.createdAt<:endDate order by o.createdAt desc, o.id desc")
 List<Order> findStatementOrders(@Param("retailerId") UUID retailerId,@Param("distributorId") UUID distributorId,@Param("startDate") Instant startDate,@Param("endDate") Instant endDate);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select o from Order o where o.id=:id")
 Optional<Order> findForUpdate(@Param("id") UUID id);
 @Query("select distinct o from Order o join fetch o.retailer r left join fetch o.items where (:status is null or o.status=:status) and (:retailerId is null or r.id=:retailerId) and (:startDate is null or o.createdAt>=:startDate) and (:endDate is null or o.createdAt<:endDate)")
 Page<Order> search(@Param("status") OrderStatus status,@Param("retailerId") UUID retailerId,@Param("startDate") Instant startDate,@Param("endDate") Instant endDate,Pageable pageable);
 @Query("select distinct o from Order o join fetch o.retailer r left join fetch o.items where o.distributor.id in :distributorIds and (:status is null or o.status=:status) and (:retailerId is null or r.id=:retailerId) and o.createdAt>=:startDate and o.createdAt<:endDate and (cast(:search as string) is null or lower(o.orderNumber) like concat('%',cast(:search as string),'%') or lower(cast(o.id as string)) like concat('%',cast(:search as string),'%') or lower(r.businessName) like concat('%',cast(:search as string),'%') or lower(coalesce(r.contactPhone,'')) like concat('%',cast(:search as string),'%'))")
 Page<Order> searchByDistributorIds(@Param("distributorIds") Collection<UUID> distributorIds,@Param("status") OrderStatus status,@Param("retailerId") UUID retailerId,@Param("startDate") Instant startDate,@Param("endDate") Instant endDate,@Param("search") String search,Pageable pageable);
 @Query("select distinct o from Order o join fetch o.retailer r left join fetch o.items where o.id in :ids")
 List<Order> findDetailedByIdIn(@Param("ids") Collection<UUID> ids);
 @Query("select new com.vyapaaros.order.dto.OrderStatusCount(o.status, count(o)) from Order o where o.distributor.id in :distributorIds group by o.status")
 List<OrderStatusCount> countByStatusForDistributors(@Param("distributorIds") Collection<UUID> distributorIds);
 @Query("select o from Order o where o.distributor.id in :distributorIds order by o.createdAt desc, o.id desc")
 List<Order> findRecentByDistributorIds(@Param("distributorIds") Collection<UUID> distributorIds, Pageable pageable);
}
