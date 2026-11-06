package com.vyapaaros.retailer.repository;

import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.entity.RetailerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface RetailerRepository extends JpaRepository<Retailer, UUID> {

    Optional<Retailer> findByGstinIgnoreCase(String gstin);

    Optional<Retailer> findFirstByContactEmailIgnoreCaseOrderByCreatedAtAsc(
        String contactEmail
    );

    boolean existsByGstinIgnoreCase(String gstin);

    List<Retailer> findByStatus(RetailerStatus status);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Retailer> findById(UUID id);
}
