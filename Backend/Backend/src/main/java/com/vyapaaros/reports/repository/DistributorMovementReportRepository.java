package com.vyapaaros.reports.repository;

import com.vyapaaros.inventory.entity.InventoryMovement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DistributorMovementReportRepository extends JpaRepository<InventoryMovement, UUID> {
    @Query("select m.movementType as movementType, count(m) as movementCount, coalesce(sum(m.onHandDelta),0) as onHandDeltaCases, coalesce(sum(m.reservedDelta),0) as reservedDeltaCases from InventoryMovement m where m.distributor.id=:distributorId and m.createdAt>=:fromTime and m.createdAt<:untilTime group by m.movementType")
    List<MovementAggregate> summarize(@Param("distributorId") UUID distributorId,
        @Param("fromTime") Instant fromTime, @Param("untilTime") Instant untilTime);
}
