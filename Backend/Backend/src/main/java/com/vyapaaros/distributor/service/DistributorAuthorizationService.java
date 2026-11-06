package com.vyapaaros.distributor.service;

import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.entity.DistributorMembershipStatus;
import com.vyapaaros.distributor.entity.RetailerDistributorRelationship.RelationshipStatus;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.distributor.repository.DistributorMembershipRepository;
import com.vyapaaros.distributor.repository.RetailerDistributorRelationshipRepository;
import com.vyapaaros.retailer.service.RetailerAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DistributorAuthorizationService {
    private final DistributorBusinessRepository businesses;
    private final DistributorMembershipRepository memberships;
    private final RetailerDistributorRelationshipRepository relationships;
    private final RetailerAuthorizationService retailerAuthorization;

    public DistributorAuthorizationService(DistributorBusinessRepository businesses,
        DistributorMembershipRepository memberships,
        RetailerDistributorRelationshipRepository relationships) {
        this(businesses, memberships, relationships, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DistributorAuthorizationService(DistributorBusinessRepository businesses,
        DistributorMembershipRepository memberships,
        RetailerDistributorRelationshipRepository relationships,
        RetailerAuthorizationService retailerAuthorization) {
        this.businesses = businesses;
        this.memberships = memberships;
        this.relationships = relationships;
        this.retailerAuthorization = retailerAuthorization;
    }

    public boolean hasActiveOrderDeskAccess(UUID userId, UUID distributorId) {
        return isOriginalCreator(userId, distributorId)
            || userId != null && distributorId != null
                && memberships.existsByDistributorIdAndUserIdAndStatus(distributorId, userId, DistributorMembershipStatus.ACTIVE);
    }

    public void requireOrderDeskAccess(UUID userId, UUID distributorId) {
        if (!hasActiveOrderDeskAccess(userId, distributorId)) throw denied();
    }

    public boolean isOriginalCreator(UUID userId, UUID distributorId) {
        if (userId == null || distributorId == null) return false;
        return businesses.findById(distributorId).map(b -> userId.equals(b.getCreatedBy().getId())).orElse(false);
    }

    public void requireOriginalCreator(UUID userId, UUID distributorId) {
        if (!isOriginalCreator(userId, distributorId)) throw denied();
    }

    public boolean isRetailerLinked(UUID retailerId, UUID distributorId) {
        return retailerId != null && distributorId != null
            && relationships.existsByRetailerIdAndDistributorIdAndStatus(retailerId, distributorId, RelationshipStatus.ACTIVE);
    }

    public void requireActiveRetailerLink(UUID retailerId, UUID distributorId) {
        if (!isRetailerLinked(retailerId, distributorId)) throw denied();
    }

    public UUID requireRetailerAccess(UUID userId, UUID retailerId) {
        if (userId == null || retailerId == null) throw denied();
        if (retailerAuthorization != null) retailerAuthorization.requireMembership(userId, retailerId);
        return relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId, RelationshipStatus.ACTIVE)
            .map(relationship -> relationship.getDistributor().getId())
            .orElseThrow(this::denied);
    }

    public UUID requireOrderDeskBusiness(UUID userId, UUID distributorId) {
        requireOrderDeskAccess(userId, distributorId);
        return distributorId;
    }

    public Set<UUID> activeOrderDeskBusinessIds(UUID userId) {
        if (userId == null) throw denied();
        Set<UUID> ids = memberships.findByUserIdAndStatus(userId, DistributorMembershipStatus.ACTIVE).stream()
            .map(membership -> membership.getDistributor().getId()).collect(Collectors.toSet());
        businesses.findAllByCreatedById(userId).stream().map(DistributorBusiness::getId).forEach(ids::add);
        if (ids.isEmpty()) throw denied();
        return Set.copyOf(ids);
    }

    public UUID requireBusinessAccess(UUID userId, UUID retailerId, UUID requestedDistributorId, boolean orderDesk) {
        if (orderDesk) {
            if (requestedDistributorId == null) throw denied();
            return requireOrderDeskBusiness(userId, requestedDistributorId);
        }
        if (retailerId == null) throw denied();
        UUID currentDistributorId = requireRetailerAccess(userId, retailerId);
        if (requestedDistributorId != null && !requestedDistributorId.equals(currentDistributorId)) throw denied();
        return currentDistributorId;
    }

    public DistributorBusiness requireCurrentDistributorForRetailer(UUID retailerId) {
        return relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId, RelationshipStatus.ACTIVE)
            .map(relationship -> relationship.getDistributor())
            .orElseThrow(this::denied);
    }

    private AccessDeniedException denied() { return new AccessDeniedException("Distributor business access denied."); }
}
