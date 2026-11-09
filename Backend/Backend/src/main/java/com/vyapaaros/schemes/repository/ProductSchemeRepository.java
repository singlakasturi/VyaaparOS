package com.vyapaaros.schemes.repository;

import com.vyapaaros.schemes.entity.ProductScheme;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductSchemeRepository extends JpaRepository<ProductScheme, UUID> {
    @Query("""
        select s from ProductScheme s where s.active = true
        and (s.product.id in :productIds or s.category.id in (select p.category.id from Product p where p.id in :productIds) or (s.product is null and s.category is null))
        and s.distributor.id = :distributorId
        and (s.retailer is null or s.retailer.id = :retailerId)
        and (s.startsAt is null or s.startsAt <= :at)
        and (s.endsAt is null or s.endsAt > :at)
        """)
    List<ProductScheme> findApplicable(@Param("productIds") Collection<UUID> productIds,
        @Param("retailerId") UUID retailerId, @Param("distributorId") UUID distributorId,
        @Param("at") java.time.Instant at);

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

    @Query("select s from ProductScheme s left join fetch s.product left join fetch s.category left join fetch s.retailer where s.distributor.id = :distributorId and (:productId is null or s.product.id = :productId) and (:categoryId is null or s.category.id = :categoryId) and (:retailerId is null or s.retailer.id = :retailerId) and (:active is null or s.active = :active) order by s.createdAt desc")
    List<ProductScheme> searchByDistributor(@Param("distributorId") UUID distributorId,
        @Param("productId") UUID productId, @Param("categoryId") UUID categoryId,
        @Param("retailerId") UUID retailerId, @Param("active") Boolean active);

    @Query("select s from ProductScheme s left join fetch s.product left join fetch s.category left join fetch s.retailer where s.distributor.id = :distributorId and (cast(:q as string) is null or lower(s.name) like lower(concat('%', cast(:q as string), '%')) or lower(coalesce(s.description, '')) like lower(concat('%', cast(:q as string), '%'))) and (:active is null or s.active = :active) order by s.createdAt desc")
    List<ProductScheme> searchManaged(@Param("distributorId") UUID distributorId,
        @Param("q") String query, @Param("active") Boolean active);
}
