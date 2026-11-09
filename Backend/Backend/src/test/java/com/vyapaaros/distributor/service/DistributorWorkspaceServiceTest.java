package com.vyapaaros.distributor.service;

import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.entity.DistributorMembership;
import com.vyapaaros.distributor.entity.DistributorMembershipStatus;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.distributor.repository.DistributorMembershipRepository;
import com.vyapaaros.distributor.repository.RetailerDistributorRelationshipRepository;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DistributorWorkspaceServiceTest {
    private final DistributorBusinessRepository businesses = mock(DistributorBusinessRepository.class);
    private final DistributorMembershipRepository memberships = mock(DistributorMembershipRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DistributorAuthorizationService authorization = mock(DistributorAuthorizationService.class);
    private final DistributorWorkspaceService service =
        new DistributorWorkspaceService(businesses, memberships, users, authorization);

    private final UUID ownerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID unrelatedId = UUID.randomUUID();
    private final UUID distributorId = UUID.randomUUID();
    private User owner;
    private User staff;
    private DistributorBusiness business;

    @BeforeEach
    void setUp() {
        reset(businesses, memberships, users, authorization);
        owner = user(ownerId, "owner@example.com", "Owner");
        staff = user(staffId, "staff@example.com", "Staff");
        business = business(distributorId, owner);
        when(authorization.activeOrderDeskBusinessIds(ownerId)).thenReturn(Set.of(distributorId));
        when(authorization.activeOrderDeskBusinessIds(staffId)).thenReturn(Set.of(distributorId));
        when(authorization.activeOrderDeskBusinessIds(unrelatedId)).thenReturn(Set.of());
        when(businesses.findById(distributorId)).thenReturn(Optional.of(business));
        when(businesses.findAllByCreatedById(any(UUID.class))).thenReturn(List.of());
        when(memberships.existsByUserIdAndStatusAndDistributorIdNot(
            any(UUID.class), eq(DistributorMembershipStatus.ACTIVE), eq(distributorId))).thenReturn(false);
    }

    @Test
    void ownerCanReadAndUpdateBusinessDetails() {
        var read = service.getBusiness(ownerId);
        when(businesses.save(any(DistributorBusiness.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var updated = service.updateBusiness(ownerId, "  North Distribution  ");

        assertTrue(read.owner());
        assertTrue(read.canManageStaff());
        assertEquals("Owner", read.ownerName());
        assertEquals("North Distribution", updated.businessName());
        assertEquals("Owner", updated.ownerName());
        assertEquals(owner, business.getCreatedBy());
        assertEquals(distributorId, business.getId());
        assertNotNull(business.getUpdatedAt());
        verify(authorization).requireOriginalCreator(ownerId, distributorId);
    }

    @Test
    void invalidBusinessNamesAreRejectedWithoutChangingProfile() {
        Instant createdAt = business.getCreatedAt();
        assertThrows(IllegalArgumentException.class, () -> service.updateBusiness(ownerId, "  "));
        assertThrows(IllegalArgumentException.class, () -> service.updateBusiness(ownerId, "x".repeat(201)));

        assertEquals("Distributor", business.getBusinessName());
        assertEquals(createdAt, business.getCreatedAt());
        assertEquals(owner, business.getCreatedBy());
        verify(businesses, never()).save(any(DistributorBusiness.class));
    }

    @Test
    void activeStaffCanReadBusinessButCannotEditOrManageStaff() {
        var details = service.getBusiness(staffId);
        assertFalse(details.owner());
        assertFalse(details.canManageStaff());
        assertEquals("Owner", details.ownerName());

        doThrow(new AccessDeniedException("denied"))
            .when(authorization).requireOriginalCreator(staffId, distributorId);
        assertThrows(AccessDeniedException.class, () -> service.updateBusiness(staffId, "Changed"));
        assertThrows(AccessDeniedException.class, () -> service.listStaff(staffId));
        assertThrows(AccessDeniedException.class, () -> service.addStaff(staffId, "new@example.com"));
        assertThrows(AccessDeniedException.class, () -> service.revokeStaff(staffId, unrelatedId));
    }

    @Test
    void userWithoutActiveBusinessAndAmbiguousBusinessAccessAreDenied() {
        assertThrows(AccessDeniedException.class, () -> service.getBusiness(unrelatedId));
        when(authorization.activeOrderDeskBusinessIds(staffId)).thenReturn(Set.of(distributorId, UUID.randomUUID()));
        assertThrows(AccessDeniedException.class, () -> service.getBusiness(staffId));
    }

    @Test
    void revokedStaffMembershipCannotReadOrUpdateBusinessSettings() {
        when(authorization.activeOrderDeskBusinessIds(staffId)).thenReturn(Set.of());
        assertThrows(AccessDeniedException.class, () -> service.getBusiness(staffId));
        assertThrows(AccessDeniedException.class, () -> service.updateBusiness(staffId, "Changed"));
        verify(businesses, never()).save(any(DistributorBusiness.class));
    }

    @Test
    void ownerCanListOnlyActiveStaffAndOwnerIsNotListedAsStaff() {
        DistributorMembership ownerMembership = membership(owner, business);
        DistributorMembership staffMembership = membership(staff, business);
        when(memberships.findByDistributorIdAndStatusOrderByGrantedAtAsc(
            distributorId, DistributorMembershipStatus.ACTIVE)).thenReturn(List.of(ownerMembership, staffMembership));

        var result = service.listStaff(ownerId);

        assertEquals(1, result.size());
        assertEquals(staffId, result.get(0).userId());
        assertEquals("staff@example.com", result.get(0).email());
    }

    @Test
    void ownerCanAddAnExistingActiveAccountAndEmailIsNormalized() {
        when(users.findByEmail("new.staff@example.com")).thenReturn(Optional.of(user(
            UUID.randomUUID(), "new.staff@example.com", "New Staff")));
        when(memberships.saveAndFlush(any(DistributorMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.addStaff(ownerId, " New.Staff@Example.com ");

        assertEquals("new.staff@example.com", result.email());
        verify(users).findByEmail("new.staff@example.com");
        verify(memberships).saveAndFlush(argThat(membership ->
            membership.getDistributor() == business
                && membership.getStatus() == DistributorMembershipStatus.ACTIVE
                && membership.getGrantedAt() != null));
    }

    @Test
    void ownerCanRevokeStaffAndMembershipHistoryIsPreserved() {
        DistributorMembership active = membership(staff, business);
        when(memberships.findFirstByDistributorIdAndUserIdAndStatusOrderByGrantedAtDesc(
            distributorId, staffId, DistributorMembershipStatus.ACTIVE)).thenReturn(Optional.of(active));

        service.revokeStaff(ownerId, staffId);

        assertEquals(DistributorMembershipStatus.REVOKED, active.getStatus());
        assertNotNull(active.getRevokedAt());
        verify(memberships).save(active);
    }

    @Test
    void revokedMembershipLosesDistributorAccessImmediately() {
        DistributorMembership active = membership(staff, business);
        when(businesses.findById(distributorId)).thenReturn(Optional.of(business));
        when(businesses.findAllByCreatedById(ownerId)).thenReturn(List.of(business));
        when(businesses.findAllByCreatedById(staffId)).thenReturn(List.of());
        when(memberships.findByUserIdAndStatus(staffId, DistributorMembershipStatus.ACTIVE))
            .thenAnswer(invocation -> active.getStatus() == DistributorMembershipStatus.ACTIVE ? List.of(active) : List.of());
        when(memberships.existsByDistributorIdAndUserIdAndStatus(
            distributorId, staffId, DistributorMembershipStatus.ACTIVE))
            .thenAnswer(invocation -> active.getStatus() == invocation.getArgument(2));
        when(memberships.findFirstByDistributorIdAndUserIdAndStatusOrderByGrantedAtDesc(
            distributorId, staffId, DistributorMembershipStatus.ACTIVE)).thenReturn(Optional.of(active));
        var currentAuthorization = new DistributorAuthorizationService(
            businesses, memberships, mock(RetailerDistributorRelationshipRepository.class));
        var currentWorkspace = new DistributorWorkspaceService(businesses, memberships, users, currentAuthorization);

        assertTrue(currentAuthorization.hasActiveOrderDeskAccess(staffId, distributorId));
        currentWorkspace.revokeStaff(ownerId, staffId);
        assertFalse(currentAuthorization.hasActiveOrderDeskAccess(staffId, distributorId));
        assertThrows(AccessDeniedException.class, () -> currentAuthorization.activeOrderDeskBusinessIds(staffId));
    }

    @Test
    void ownerCannotRevokeSelfAndStaffCannotRevokeAnyone() {
        assertThrows(ResourceConflictException.class, () -> service.revokeStaff(ownerId, ownerId));
        doThrow(new AccessDeniedException("denied"))
            .when(authorization).requireOriginalCreator(staffId, distributorId);
        assertThrows(AccessDeniedException.class, () -> service.revokeStaff(staffId, unrelatedId));
        verify(memberships, never()).save(any(DistributorMembership.class));
    }

    @Test
    void duplicateActiveMembershipAndAccountAlreadyAtAnotherBusinessAreRejected() {
        User another = user(UUID.randomUUID(), "another@example.com", "Another");
        when(users.findByEmail("another@example.com")).thenReturn(Optional.of(another));
        when(memberships.existsByDistributorIdAndUserIdAndStatus(
            distributorId, another.getId(), DistributorMembershipStatus.ACTIVE)).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> service.addStaff(ownerId, another.getEmail()));

        reset(memberships);
        when(memberships.existsByDistributorIdAndUserIdAndStatus(
            distributorId, another.getId(), DistributorMembershipStatus.ACTIVE)).thenReturn(false);
        when(memberships.existsByUserIdAndStatusAndDistributorIdNot(
            another.getId(), DistributorMembershipStatus.ACTIVE, distributorId)).thenReturn(true);
        when(authorization.activeOrderDeskBusinessIds(ownerId)).thenReturn(Set.of(distributorId));
        when(businesses.findById(distributorId)).thenReturn(Optional.of(business));
        when(users.findByEmail("another@example.com")).thenReturn(Optional.of(another));
        assertThrows(ResourceConflictException.class, () -> service.addStaff(ownerId, another.getEmail()));
    }

    @Test
    void ownerCannotAddThemselfAndUnknownOrInactiveAccountsAreNotAdded() {
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        assertThrows(ResourceConflictException.class, () -> service.addStaff(ownerId, owner.getEmail()));
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.addStaff(ownerId, "missing@example.com"));
        User inactive = user(UUID.randomUUID(), "inactive@example.com", "Inactive");
        inactive.setStatus("SUSPENDED");
        when(users.findByEmail("inactive@example.com")).thenReturn(Optional.of(inactive));
        assertThrows(ResourceNotFoundException.class, () -> service.addStaff(ownerId, inactive.getEmail()));
    }

    @Test
    void crossDistributorStaffIdsCannotRevokeAnotherBusinessesMembership() {
        when(memberships.findFirstByDistributorIdAndUserIdAndStatusOrderByGrantedAtDesc(
            distributorId, staffId, DistributorMembershipStatus.ACTIVE)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.revokeStaff(ownerId, staffId));
        verify(memberships, never()).save(any(DistributorMembership.class));
    }

    private User user(UUID id, String email, String name) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(name);
        user.setStatus("ACTIVE");
        return user;
    }

    private DistributorBusiness business(UUID id, User creator) {
        DistributorBusiness value = new DistributorBusiness();
        value.setId(id);
        value.setBusinessName("Distributor");
        value.setCreatedBy(creator);
        value.setCreatedAt(Instant.now());
        value.setUpdatedAt(Instant.now());
        return value;
    }

    private DistributorMembership membership(User user, DistributorBusiness distributor) {
        DistributorMembership membership = new DistributorMembership();
        membership.setUser(user);
        membership.setDistributor(distributor);
        membership.setStatus(DistributorMembershipStatus.ACTIVE);
        membership.setGrantedAt(Instant.now());
        return membership;
    }
}
