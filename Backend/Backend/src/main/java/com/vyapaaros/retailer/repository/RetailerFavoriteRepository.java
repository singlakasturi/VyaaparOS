package com.vyapaaros.retailer.repository;

import com.vyapaaros.retailer.entity.RetailerFavorite;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetailerFavoriteRepository extends JpaRepository<RetailerFavorite, UUID> {
    @EntityGraph(attributePaths = {"product", "product.category", "product.distributor", "retailer", "distributor"})
    List<RetailerFavorite> findByRetailerIdAndDistributorIdOrderBySavedAtDesc(UUID retailerId, UUID distributorId);

    @EntityGraph(attributePaths = {"product", "product.category", "product.distributor", "retailer", "distributor"})
    Optional<RetailerFavorite> findByRetailerIdAndDistributorIdAndProductId(UUID retailerId, UUID distributorId, UUID productId);

    @Modifying
    @Query(value = "insert into retailer_favorites(id, retailer_id, distributor_id, product_id, saved_at) " +
        "values (:id, :retailerId, :distributorId, :productId, :savedAt) " +
        "on conflict (retailer_id, distributor_id, product_id) do nothing", nativeQuery = true)
    int insertIfMissing(@Param("id") UUID id, @Param("retailerId") UUID retailerId,
        @Param("distributorId") UUID distributorId, @Param("productId") UUID productId,
        @Param("savedAt") Instant savedAt);

    long deleteByRetailerIdAndDistributorIdAndProductId(UUID retailerId, UUID distributorId, UUID productId);
}
