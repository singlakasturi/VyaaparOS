package com.vyapaaros.retailer.repository;

import com.vyapaaros.retailer.entity.RetailerMembership;
import com.vyapaaros.retailer.entity.RetailerMembershipRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetailerMembershipRepository
    extends JpaRepository<RetailerMembership, UUID> {

    Optional<RetailerMembership> findByUserIdAndRetailerId(
        UUID userId,
        UUID retailerId
    );

    @EntityGraph(attributePaths = "retailer")
    List<RetailerMembership> findByUserId(UUID userId);

    List<RetailerMembership> findByRetailerId(UUID retailerId);

    Optional<RetailerMembership> findByRetailerIdAndRole(
        UUID retailerId,
        RetailerMembershipRole role
    );

    @EntityGraph(attributePaths = "user")
    Optional<RetailerMembership> findFirstByRetailerIdAndRole(UUID retailerId, RetailerMembershipRole role);
}
