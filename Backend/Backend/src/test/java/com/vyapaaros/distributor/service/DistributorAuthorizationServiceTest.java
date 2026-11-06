package com.vyapaaros.distributor.service;

import com.vyapaaros.distributor.entity.*;
import com.vyapaaros.distributor.entity.RetailerDistributorRelationship.RelationshipStatus;
import com.vyapaaros.distributor.repository.*;
import com.vyapaaros.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DistributorAuthorizationServiceTest {
    private final DistributorBusinessRepository businesses = mock(DistributorBusinessRepository.class);
    private final DistributorMembershipRepository memberships = mock(DistributorMembershipRepository.class);
    private final RetailerDistributorRelationshipRepository relationships = mock(RetailerDistributorRelationshipRepository.class);
    private final DistributorAuthorizationService service = new DistributorAuthorizationService(businesses, memberships, relationships);
    private final UUID creatorId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID distributorId = UUID.randomUUID();
    private final UUID retailerId = UUID.randomUUID();

    @BeforeEach
    void resetMocks() { reset(businesses, memberships, relationships); }

    @Test
    void originalCreatorHasAccessAndAloneHasStaffManagementAuthority() {
        User creator = user(creatorId);
        DistributorBusiness business = business(distributorId, creator);
        when(businesses.findById(distributorId)).thenReturn(Optional.of(business));

        assertTrue(service.hasActiveOrderDeskAccess(creatorId, distributorId));
        service.requireOrderDeskAccess(creatorId, distributorId);
        service.requireOriginalCreator(creatorId, distributorId);
        assertFalse(service.isOriginalCreator(staffId, distributorId));
        verifyNoInteractions(memberships);
    }

    @Test
    void activeMembershipGrantsAccessButRevokedOrMissingMembershipDoesNot() {
        when(businesses.findById(distributorId)).thenReturn(Optional.empty());
        when(memberships.existsByDistributorIdAndUserIdAndStatus(distributorId, staffId, DistributorMembershipStatus.ACTIVE))
            .thenReturn(true);

        assertTrue(service.hasActiveOrderDeskAccess(staffId, distributorId));
        service.requireOrderDeskAccess(staffId, distributorId);
        when(memberships.existsByDistributorIdAndUserIdAndStatus(distributorId, staffId, DistributorMembershipStatus.ACTIVE))
            .thenReturn(false);
        assertFalse(service.hasActiveOrderDeskAccess(staffId, distributorId));
        assertThrows(AccessDeniedException.class, () -> service.requireOrderDeskAccess(staffId, distributorId));
        assertThrows(AccessDeniedException.class, () -> service.requireOriginalCreator(staffId, distributorId));
    }

    @Test
    void missingAndNullBusinessAccessIsDeniedWithoutLegacyRoleBypass() {
        when(businesses.findById(distributorId)).thenReturn(Optional.empty());
        when(memberships.existsByDistributorIdAndUserIdAndStatus(distributorId, staffId, DistributorMembershipStatus.ACTIVE)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.requireOrderDeskAccess(staffId, distributorId));
        assertThrows(AccessDeniedException.class, () -> service.requireOrderDeskAccess(null, distributorId));
        assertFalse(service.isOriginalCreator(null, distributorId));
        verify(memberships, never()).existsByDistributorIdAndUserIdAndStatus(distributorId, null, DistributorMembershipStatus.ACTIVE);
    }

    @Test
    void onlyCurrentActiveRetailerRelationshipIsAccepted() {
        when(relationships.existsByRetailerIdAndDistributorIdAndStatus(retailerId, distributorId, RelationshipStatus.ACTIVE))
            .thenReturn(true);
        assertTrue(service.isRetailerLinked(retailerId, distributorId));
        service.requireActiveRetailerLink(retailerId, distributorId);
        when(relationships.existsByRetailerIdAndDistributorIdAndStatus(retailerId, distributorId, RelationshipStatus.ACTIVE))
            .thenReturn(false);
        assertFalse(service.isRetailerLinked(retailerId, distributorId));
        assertThrows(AccessDeniedException.class, () -> service.requireActiveRetailerLink(retailerId, distributorId));
        assertFalse(service.isRetailerLinked(null, distributorId));
    }

    @Test
    void currentRelationshipMustExistToResolveDistributorForNewOrder() {
        when(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId, RelationshipStatus.ACTIVE))
            .thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> service.requireCurrentDistributorForRetailer(retailerId));
    }

    private User user(UUID id) { User user = new User(); user.setId(id); return user; }
    private DistributorBusiness business(UUID id, User creator) {
        DistributorBusiness business = new DistributorBusiness();
        business.setId(id); business.setCreatedBy(creator); business.setCreatedAt(Instant.now()); business.setUpdatedAt(Instant.now());
        return business;
    }
}
