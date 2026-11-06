package com.vyapaaros.distributor.repository;

import com.vyapaaros.distributor.entity.RetailerDistributorRelationship;
import com.vyapaaros.distributor.entity.RetailerDistributorRelationship.RelationshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RetailerDistributorRelationshipRepository extends JpaRepository<RetailerDistributorRelationship, UUID> {
    boolean existsByRetailerIdAndDistributorIdAndStatus(UUID retailerId, UUID distributorId, RelationshipStatus status);
    Optional<RetailerDistributorRelationship> findFirstByRetailerIdAndStatusOrderByStartedAtDesc(UUID retailerId, RelationshipStatus status);
    Optional<RetailerDistributorRelationship> findFirstByRetailerIdAndDistributorIdAndStatus(UUID retailerId, UUID distributorId, RelationshipStatus status);
}
