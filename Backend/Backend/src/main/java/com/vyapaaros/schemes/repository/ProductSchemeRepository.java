package com.vyapaaros.schemes.repository;

import com.vyapaaros.schemes.entity.ProductScheme;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductSchemeRepository extends JpaRepository<ProductScheme, UUID> {
    @Query("""
        select s from ProductScheme s where s.product.id in :productIds and s.active = true
        and (s.retailer is null or s.retailer.id = :retailerId)
        and (s.startsAt is null or s.startsAt <= :at)
        and (s.endsAt is null or s.endsAt > :at)
        """)
    List<ProductScheme> findApplicable(@Param("productIds") Collection<UUID> productIds,
        @Param("retailerId") UUID retailerId, @Param("at") java.time.Instant at);
    List<ProductScheme> findByProductIdAndActiveTrue(UUID productId);
    List<ProductScheme> findByRetailerId(UUID retailerId);

    @Query("""
        select s from ProductScheme s
        where (:productId is null or s.product.id = :productId)
          and (:retailerId is null or s.retailer.id = :retailerId)
          and (:active is null or s.active = :active)
        order by s.createdAt desc
        """)
    List<ProductScheme> search(@Param("productId") UUID productId,
        @Param("retailerId") UUID retailerId, @Param("active") Boolean active);

    @Query("select s from ProductScheme s where s.distributor.id = :distributorId and (:productId is null or s.product.id = :productId) and (:retailerId is null or s.retailer.id = :retailerId) and (:active is null or s.active = :active) order by s.createdAt desc")
    List<ProductScheme> searchByDistributor(@Param("distributorId") UUID distributorId,
        @Param("productId") UUID productId, @Param("retailerId") UUID retailerId, @Param("active") Boolean active);
}
