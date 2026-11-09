package com.vyapaaros.distributor.repository;

import com.vyapaaros.distributor.entity.DistributorMembership;
import com.vyapaaros.distributor.entity.DistributorMembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface DistributorMembershipRepository extends JpaRepository<DistributorMembership, UUID> {
    Optional<DistributorMembership> findByDistributorIdAndUserId(UUID distributorId, UUID userId);
    Optional<DistributorMembership> findFirstByDistributorIdAndUserIdAndStatusOrderByGrantedAtDesc(
        UUID distributorId, UUID userId, DistributorMembershipStatus status);
    boolean existsByDistributorIdAndUserIdAndStatus(UUID distributorId, UUID userId, DistributorMembershipStatus status);
    boolean existsByUserIdAndStatusAndDistributorIdNot(UUID userId, DistributorMembershipStatus status, UUID distributorId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "user")
    List<DistributorMembership> findByDistributorIdAndStatusOrderByGrantedAtAsc(
        UUID distributorId, DistributorMembershipStatus status);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "distributor")
    List<DistributorMembership> findByUserIdAndStatus(UUID userId, DistributorMembershipStatus status);
}
