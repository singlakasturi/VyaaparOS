package com.vyapaaros.inventory.repository;

import com.vyapaaros.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductIdAndDistributorId(UUID productId, UUID distributorId);
    List<Inventory> findByProductIdInAndDistributorId(Collection<UUID> productIds, UUID distributorId);

    @Query("select i.availableCases from Inventory i where i.product.id=:productId and i.distributor.id=:distributorId")
    Optional<Integer> findAvailableCasesByProductIdAndDistributorId(@Param("productId") UUID productId,
        @Param("distributorId") UUID distributorId);

    @Modifying
    @Query("update Inventory i set i.availableCases=i.availableCases-:quantity, i.updatedAt=CURRENT_TIMESTAMP where i.product.id=:productId and i.distributor.id=:distributorId and i.availableCases >= :quantity")
    int reserve(@Param("productId") UUID productId, @Param("distributorId") UUID distributorId,
        @Param("quantity") int quantity);

    @Modifying
    @Query("update Inventory i set i.availableCases=i.availableCases+:quantity, i.updatedAt=CURRENT_TIMESTAMP where i.product.id=:productId and i.distributor.id=:distributorId")
    int restore(@Param("productId") UUID productId, @Param("distributorId") UUID distributorId,
        @Param("quantity") int quantity);
}
