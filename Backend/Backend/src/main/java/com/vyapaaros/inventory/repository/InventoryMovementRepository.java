package com.vyapaaros.inventory.repository;

import com.vyapaaros.inventory.entity.InventoryMovement;
import com.vyapaaros.inventory.entity.InventoryMovementType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
    boolean existsByEventKey(String eventKey);

    @EntityGraph(attributePaths = {"product", "order", "actor"})
    @Query(value = "select m from InventoryMovement m left join m.order o where m.distributor.id=:distributorId and (:productId is null or m.product.id=:productId) and (:movementType is null or m.movementType=:movementType) and (:fromTime is null or m.createdAt>=:fromTime) and (:untilTime is null or m.createdAt<:untilTime) and (cast(:orderReference as string) is null or lower(o.orderNumber) like concat('%',cast(:orderReference as string),'%') or (cast(:orderId as string) is not null and o.id=:orderId))",
        countQuery = "select count(m) from InventoryMovement m left join m.order o where m.distributor.id=:distributorId and (:productId is null or m.product.id=:productId) and (:movementType is null or m.movementType=:movementType) and (:fromTime is null or m.createdAt>=:fromTime) and (:untilTime is null or m.createdAt<:untilTime) and (cast(:orderReference as string) is null or lower(o.orderNumber) like concat('%',cast(:orderReference as string),'%') or (cast(:orderId as string) is not null and o.id=:orderId))")
    Page<InventoryMovement> searchForDistributor(@Param("distributorId") UUID distributorId,
        @Param("productId") UUID productId, @Param("movementType") InventoryMovementType movementType,
        @Param("fromTime") Instant fromTime, @Param("untilTime") Instant untilTime,
        @Param("orderReference") String orderReference, @Param("orderId") UUID orderId, Pageable pageable);

    Optional<InventoryMovement> findFirstByInventoryIdAndMovementTypeOrderByCreatedAtAscIdAsc(UUID inventoryId, InventoryMovementType movementType);

    @Query("select coalesce(sum(m.onHandDelta),0) from InventoryMovement m where m.inventory.id=:inventoryId and m.movementType<>:opening and m.createdAt>:afterTime")
    Long sumOnHandDeltas(@Param("inventoryId") UUID inventoryId, @Param("opening") InventoryMovementType opening, @Param("afterTime") Instant afterTime);

    @Query("select coalesce(sum(m.reservedDelta),0) from InventoryMovement m where m.inventory.id=:inventoryId and m.movementType<>:opening and m.createdAt>:afterTime")
    Long sumReservedDeltas(@Param("inventoryId") UUID inventoryId, @Param("opening") InventoryMovementType opening, @Param("afterTime") Instant afterTime);
}
