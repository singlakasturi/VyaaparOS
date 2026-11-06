package com.vyapaaros.distributor.repository;

import com.vyapaaros.distributor.entity.DistributorBusiness;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface DistributorBusinessRepository extends JpaRepository<DistributorBusiness, UUID> {
    List<DistributorBusiness> findAllByCreatedById(UUID createdById);
}
