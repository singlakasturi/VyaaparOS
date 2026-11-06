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
