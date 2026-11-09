package com.vyapaaros.catalog.repository;

import com.vyapaaros.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    Optional<Product> findByIdAndActiveTrue(UUID id);
    Optional<Product> findByIdAndDistributorIdAndActiveTrue(UUID id, UUID distributorId);
    Optional<Product> findByIdAndDistributorId(UUID id, UUID distributorId);
    boolean existsByDistributorIdAndSkuIgnoreCase(UUID distributorId, String sku);
    Optional<Product> findByDistributorIdAndSkuIgnoreCase(UUID distributorId, String sku);
    boolean existsByDistributorIdAndSkuIgnoreCaseAndIdNot(UUID distributorId, String sku, UUID id);
    @org.springframework.data.jpa.repository.Query("""
        select p from Product p join fetch p.category c
        where p.distributor.id = :distributorId
          and (:active is null or p.active = :active)
          and (cast(:q as string) is null or lower(p.name) like lower(concat('%', cast(:q as string), '%'))
            or lower(p.sku) like lower(concat('%', cast(:q as string), '%')))
        order by lower(p.name), lower(p.sku)
        """)
    java.util.List<Product> findManagedByDistributor(@org.springframework.data.repository.query.Param("distributorId") UUID distributorId,
        @org.springframework.data.repository.query.Param("q") String query,
        @org.springframework.data.repository.query.Param("active") Boolean active);
    long countByIdInAndDistributorId(Collection<UUID> ids, UUID distributorId);

    // Explicit casts keep nullable search filters typed as text in PostgreSQL.
    @Query("""
        select p
        from Product p
        join fetch p.category c
        where p.distributor.id = :distributorId
          and p.active = true
          and c.active = true
          and (cast(:q as string) is null
            or lower(p.name) like lower(concat('%', cast(:q as string), '%'))
            or lower(p.sku) like lower(concat('%', cast(:q as string), '%')))
          and (cast(:categorySlug as string) is null
            or c.slug = cast(:categorySlug as string))
        """)
    Page<Product> searchActive(
        @Param("q") String query,
        @Param("categorySlug") String categorySlug,
        @Param("distributorId") UUID distributorId,
        Pageable pageable
    );
}
