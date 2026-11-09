package com.vyapaaros.inventory.repository;

import com.vyapaaros.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductIdAndDistributorId(UUID productId, UUID distributorId);
    @Query("select i.id as inventoryId, i.onHandCases as onHandCases, i.reservedCases as reservedCases, i.availableCases as availableCases from Inventory i where i.product.id=:productId and i.distributor.id=:distributorId")
    Optional<InventoryBalanceView> findBalanceByProductIdAndDistributorId(@Param("productId") UUID productId, @Param("distributorId") UUID distributorId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.product.id = :productId and i.distributor.id = :distributorId")
    Optional<Inventory> lockByProductIdAndDistributorId(@Param("productId") UUID productId, @Param("distributorId") UUID distributorId);
    @EntityGraph(attributePaths = {"product", "product.category"})
    @Query("select i from Inventory i join fetch i.product p join fetch p.category where i.distributor.id = :distributorId order by lower(p.name), lower(p.sku)")
    List<Inventory> findAllForDistributor(@Param("distributorId") UUID distributorId);
    @EntityGraph(attributePaths = {"product", "product.category"})
    @Query(value = "select i from Inventory i join i.product p where i.distributor.id=:distributorId and (cast(:query as string) is null or lower(p.name) like concat('%',cast(:query as string),'%') or lower(p.sku) like concat('%',cast(:query as string),'%')) and (:lowStock=false or i.availableCases <= i.lowStockThreshold)",
        countQuery = "select count(i) from Inventory i join i.product p where i.distributor.id=:distributorId and (cast(:query as string) is null or lower(p.name) like concat('%',cast(:query as string),'%') or lower(p.sku) like concat('%',cast(:query as string),'%')) and (:lowStock=false or i.availableCases <= i.lowStockThreshold)")
    Page<Inventory> searchForDistributor(@Param("distributorId") UUID distributorId, @Param("query") String query,
        @Param("lowStock") boolean lowStock, Pageable pageable);
    List<Inventory> findByProductIdInAndDistributorId(Collection<UUID> productIds, UUID distributorId);

    @Query("select i.availableCases from Inventory i where i.product.id=:productId and i.distributor.id=:distributorId")
    Optional<Integer> findAvailableCasesByProductIdAndDistributorId(@Param("productId") UUID productId,
        @Param("distributorId") UUID distributorId);

    @Modifying
    @Query("update Inventory i set i.availableCases=i.availableCases-:quantity, i.reservedCases=i.reservedCases+:quantity, i.updatedAt=CURRENT_TIMESTAMP where i.product.id=:productId and i.distributor.id=:distributorId and i.availableCases >= :quantity")
    int reserve(@Param("productId") UUID productId, @Param("distributorId") UUID distributorId,
        @Param("quantity") int quantity);

    @Modifying
    @Query("update Inventory i set i.availableCases=i.availableCases+:quantity, i.reservedCases=i.reservedCases-:quantity, i.updatedAt=CURRENT_TIMESTAMP where i.product.id=:productId and i.distributor.id=:distributorId and i.reservedCases >= :quantity")
    int restore(@Param("productId") UUID productId, @Param("distributorId") UUID distributorId,
        @Param("quantity") int quantity);

    @Modifying
    @Query("update Inventory i set i.onHandCases=i.onHandCases-:quantity, i.reservedCases=i.reservedCases-:quantity, i.updatedAt=CURRENT_TIMESTAMP where i.product.id=:productId and i.distributor.id=:distributorId and i.reservedCases >= :quantity and i.onHandCases >= :quantity")
    int consumeReserved(@Param("productId") UUID productId, @Param("distributorId") UUID distributorId,
        @Param("quantity") int quantity);
}
