package com.vyapaaros.order.repository;

import com.vyapaaros.order.entity.TallyExportBatch;
import java.util.UUID;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TallyExportBatchRepository extends JpaRepository<TallyExportBatch, UUID> {
    Page<TallyExportBatch> findByDistributorIdIn(Collection<UUID> distributorIds, Pageable pageable);
}
