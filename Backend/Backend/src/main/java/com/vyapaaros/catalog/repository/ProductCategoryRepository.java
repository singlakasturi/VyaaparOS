package com.vyapaaros.catalog.repository;

import com.vyapaaros.catalog.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    List<ProductCategory> findByActiveTrueOrderByNameAsc();
    Optional<ProductCategory> findBySlugAndActiveTrue(String slug);
    List<ProductCategory> findByDistributorIdAndActiveTrueOrderByNameAsc(UUID distributorId);
    Optional<ProductCategory> findByIdAndDistributorIdAndActiveTrue(UUID id, UUID distributorId);
    Optional<ProductCategory> findByIdAndDistributorId(UUID id, UUID distributorId);
    boolean existsByDistributorIdAndSlugIgnoreCase(UUID distributorId, String slug);
}
