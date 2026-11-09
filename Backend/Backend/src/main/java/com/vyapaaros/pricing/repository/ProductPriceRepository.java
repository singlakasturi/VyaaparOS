package com.vyapaaros.pricing.repository;

import com.vyapaaros.pricing.entity.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {

    Optional<ProductPrice> findByProductIdAndDistributorIdAndRetailerIsNullAndActiveTrue(UUID productId, UUID distributorId);

    Optional<ProductPrice> findByProductIdAndDistributorIdAndRetailerIdAndActiveTrue(UUID productId, UUID distributorId, UUID retailerId);

    Optional<ProductPrice> findByProductIdAndRetailerIdAndActiveTrue(
        UUID productId,
        UUID retailerId
    );

    @Query("""
        select price
        from ProductPrice price
        where price.active = true
          and price.product.id in :productIds
          and price.distributor.id = :distributorId
          and price.retailer.id = :retailerId
        """)
    List<ProductPrice> findActiveOverrides(
        @Param("productIds") List<UUID> productIds,
        @Param("retailerId") UUID retailerId,
        @Param("distributorId") UUID distributorId
    );

    @Query("""
        select price
        from ProductPrice price
        where price.active = true
          and price.product.id in :productIds
          and price.distributor.id = :distributorId
          and price.retailer is null
        """)
    List<ProductPrice> findActiveDefaults(@Param("productIds") List<UUID> productIds,
        @Param("distributorId") UUID distributorId);

    List<ProductPrice> findByRetailerId(UUID retailerId);

    List<ProductPrice> findByProductId(UUID productId);

    @Query("""
        select price from ProductPrice price join fetch price.product product
        where price.distributor.id = :distributorId and price.retailer is null
        order by lower(product.name), lower(product.sku)
        """)
    List<ProductPrice> findManagedDefaults(@Param("distributorId") UUID distributorId);
}
