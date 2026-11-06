package com.vyapaaros.retailer.service;

import com.vyapaaros.retailer.entity.RetailerMembership;
import com.vyapaaros.retailer.entity.RetailerMembershipRole;
import com.vyapaaros.retailer.repository.RetailerMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RetailerAuthorizationServiceTest {

    private final RetailerMembershipRepository membershipRepository =
        mock(RetailerMembershipRepository.class);
    private final RetailerAuthorizationService authorizationService =
        new RetailerAuthorizationService(membershipRepository);
    private final UUID userId = UUID.randomUUID();
    private final UUID retailerId = UUID.randomUUID();

    @BeforeEach
    void resetRepository() {
        reset(membershipRepository);
    }

    @Test
    void requireMembershipAllowsEveryMembershipRoleAndUsesBothIds() {
        for (RetailerMembershipRole role : RetailerMembershipRole.values()) {
            RetailerMembership membership = membership(role);
            whenMembership(membership);

            assertSame(
                membership,
                authorizationService.requireMembership(userId, retailerId)
            );
            verify(membershipRepository).findByUserIdAndRetailerId(userId, retailerId);
            resetRepository();
        }
    }

    @Test
    void requireMembershipRejectsMissingMembership() {
        when(membershipRepository.findByUserIdAndRetailerId(userId, retailerId))
            .thenReturn(Optional.empty());

        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireMembership(userId, retailerId)
        );
        verify(membershipRepository).findByUserIdAndRetailerId(userId, retailerId);
    }

    @Test
    void requireOwnerAllowsOwner() {
        RetailerMembership membership = membership(RetailerMembershipRole.RETAILER_OWNER);
        whenMembership(membership);

        assertSame(membership, authorizationService.requireOwner(userId, retailerId));
        verifyExactMembershipLookup();
    }

    @Test
    void requireOwnerRejectsManagerAndStaff() {
        for (RetailerMembershipRole role : new RetailerMembershipRole[] {
            RetailerMembershipRole.RETAILER_MANAGER,
            RetailerMembershipRole.RETAILER_STAFF
        }) {
            RetailerMembership membership = membership(role);
            whenMembership(membership);

            assertThrows(
                AccessDeniedException.class,
                () -> authorizationService.requireOwner(userId, retailerId)
            );
            verifyExactMembershipLookup();
            resetRepository();
        }
    }

    @Test
    void requireManagerOrOwnerAllowsOwnerAndManager() {
        for (RetailerMembershipRole role : new RetailerMembershipRole[] {
            RetailerMembershipRole.RETAILER_OWNER,
            RetailerMembershipRole.RETAILER_MANAGER
        }) {
            RetailerMembership membership = membership(role);
            whenMembership(membership);

            assertSame(
                membership,
                authorizationService.requireManagerOrOwner(userId, retailerId)
            );
            verifyExactMembershipLookup();
            resetRepository();
        }
    }

    @Test
    void requireManagerOrOwnerRejectsStaff() {
        RetailerMembership membership = membership(RetailerMembershipRole.RETAILER_STAFF);
        whenMembership(membership);

        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireManagerOrOwner(userId, retailerId)
        );
        verifyExactMembershipLookup();
    }

    @Test
    void requireStaffOrAboveAllowsOwnerManagerAndStaff() {
        for (RetailerMembershipRole role : RetailerMembershipRole.values()) {
            RetailerMembership membership = membership(role);
            whenMembership(membership);

            assertSame(
                membership,
                authorizationService.requireStaffOrAbove(userId, retailerId)
            );
            verifyExactMembershipLookup();
            resetRepository();
        }
    }

    @Test
    void allAuthorizationMethodsRejectMissingMembership() {
        when(membershipRepository.findByUserIdAndRetailerId(userId, retailerId))
            .thenReturn(Optional.empty());

        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireOwner(userId, retailerId)
        );
        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireManagerOrOwner(userId, retailerId)
        );
        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireStaffOrAbove(userId, retailerId)
        );
        verify(membershipRepository, times(3))
            .findByUserIdAndRetailerId(userId, retailerId);
    }

    @Test
    void allAuthorizationMethodsRejectNullIdentifiersWithoutRepositoryAccess() {
        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireMembership(null, retailerId)
        );
        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireMembership(userId, null)
        );
        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireOwner(null, retailerId)
        );
        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireManagerOrOwner(userId, null)
        );
        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireStaffOrAbove(null, null)
        );
        verifyNoInteractions(membershipRepository);
    }

    @Test
    void allRoleChecksRejectNullMembershipRole() {
        RetailerMembership membership = membership(null);
        whenMembership(membership);

        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireOwner(userId, retailerId)
        );
        resetRepository();
        whenMembership(membership(null));
        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireManagerOrOwner(userId, retailerId)
        );
        resetRepository();
        whenMembership(membership(null));
        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireStaffOrAbove(userId, retailerId)
        );
    }

    private RetailerMembership membership(RetailerMembershipRole role) {
        RetailerMembership membership = mock(RetailerMembership.class);
        when(membership.getRole()).thenReturn(role);
        return membership;
    }

    private void whenMembership(RetailerMembership membership) {
        when(membershipRepository.findByUserIdAndRetailerId(userId, retailerId))
            .thenReturn(Optional.of(membership));
    }

    private void verifyExactMembershipLookup() {
        verify(membershipRepository).findByUserIdAndRetailerId(userId, retailerId);
    }
}
