package com.vyapaaros.reports.repository;

import com.vyapaaros.order.entity.Order;
import com.vyapaaros.order.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DistributorReportRepository extends JpaRepository<Order, UUID> {
    String INCLUDED_ACTIVITY_STATUSES = "('SUBMITTED','CREDIT_HOLD','APPROVED','TALLY_EXPORTED')";

    @Query(value = "select cast(o.created_at at time zone 'UTC' as date) as day, o.currency as currency, count(*) as \"orderCount\", coalesce(sum(o.final_amount),0) as \"orderValue\" from orders o where o.distributor_id=:distributorId and o.created_at>=:fromTime and o.created_at<:untilTime and o.status in " + INCLUDED_ACTIVITY_STATUSES + " group by day,o.currency order by day,o.currency", nativeQuery = true)
    List<DailyOrderAggregate> dailyActivity(@Param("distributorId") UUID distributorId,
        @Param("fromTime") Instant fromTime, @Param("untilTime") Instant untilTime);

    @Query(value = "select oi.product_name as \"productName\", oi.currency as currency, coalesce(sum(oi.total_cases),0) as \"orderedCases\", coalesce(sum(oi.final_amount),0) as \"orderValue\" from order_items oi join orders o on o.id=oi.order_id where o.distributor_id=:distributorId and o.created_at>=:fromTime and o.created_at<:untilTime and o.status in " + INCLUDED_ACTIVITY_STATUSES + " group by oi.product_name,oi.currency order by sum(oi.total_cases) desc, oi.product_name asc limit 10", nativeQuery = true)
    List<ProductOrderAggregate> topProductsByQuantity(@Param("distributorId") UUID distributorId,
        @Param("fromTime") Instant fromTime, @Param("untilTime") Instant untilTime);

    @Query(value = "select oi.product_name as \"productName\", oi.currency as currency, coalesce(sum(oi.total_cases),0) as \"orderedCases\", coalesce(sum(oi.final_amount),0) as \"orderValue\" from order_items oi join orders o on o.id=oi.order_id where o.distributor_id=:distributorId and o.created_at>=:fromTime and o.created_at<:untilTime and o.status in " + INCLUDED_ACTIVITY_STATUSES + " group by oi.product_name,oi.currency order by sum(oi.final_amount) desc, oi.product_name asc limit 10", nativeQuery = true)
    List<ProductOrderAggregate> topProductsByValue(@Param("distributorId") UUID distributorId,
        @Param("fromTime") Instant fromTime, @Param("untilTime") Instant untilTime);

    @Query("select o.status as status, count(o) as orderCount from Order o where o.distributor.id=:distributorId and o.createdAt>=:fromTime and o.createdAt<:untilTime group by o.status")
    List<StatusOrderAggregate> statusSummary(@Param("distributorId") UUID distributorId,
        @Param("fromTime") Instant fromTime, @Param("untilTime") Instant untilTime);
}
