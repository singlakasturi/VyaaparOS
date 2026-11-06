package com.vyapaaros.pricing.repository;

import com.vyapaaros.pricing.entity.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {

    Optional<ProductPrice> findByProductIdAndRetailerIsNullAndActiveTrue(UUID productId);

    Optional<ProductPrice> findByProductIdAndRetailerIdAndActiveTrue(
        UUID productId,
        UUID retailerId
    );

    @Query("""
        select price
        from ProductPrice price
        where price.active = true
          and price.product.id in :productIds
          and price.retailer.id = :retailerId
        """)
    List<ProductPrice> findActiveOverrides(
        @Param("productIds") List<UUID> productIds,
        @Param("retailerId") UUID retailerId
    );

    @Query("""
        select price
        from ProductPrice price
        where price.active = true
          and price.product.id in :productIds
          and price.retailer is null
        """)
    List<ProductPrice> findActiveDefaults(@Param("productIds") List<UUID> productIds);

    List<ProductPrice> findByRetailerId(UUID retailerId);

    List<ProductPrice> findByProductId(UUID productId);
}
