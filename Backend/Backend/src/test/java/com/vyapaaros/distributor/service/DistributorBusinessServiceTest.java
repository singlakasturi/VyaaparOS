package com.vyapaaros.distributor.service;

import com.vyapaaros.distributor.entity.*;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.distributor.repository.DistributorMembershipRepository;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DistributorBusinessServiceTest {
    private final DistributorBusinessRepository businesses = mock(DistributorBusinessRepository.class);
    private final DistributorMembershipRepository memberships = mock(DistributorMembershipRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DistributorBusinessService service = new DistributorBusinessService(businesses, memberships, users);

    @Test
    void creationPersistsOriginalCreatorAndActiveOrderDeskMembershipAtomically() {
        UUID creatorId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        User creator = new User();
        creator.setId(creatorId);
        creator.setStatus("ACTIVE");
        when(users.findById(creatorId)).thenReturn(Optional.of(creator));
        when(businesses.save(any(DistributorBusiness.class))).thenAnswer(invocation -> {
            DistributorBusiness business = invocation.getArgument(0);
            business.setId(businessId);
            return business;
        });

        DistributorBusiness business = service.createBusiness(creatorId, "  North Distributor  ");

        assertEquals(businessId, business.getId());
        assertEquals("North Distributor", business.getBusinessName());
        assertSame(creator, business.getCreatedBy());
        verify(memberships).save(argThat(membership ->
            membership.getDistributor() == business
                && membership.getUser() == creator
                && membership.getStatus() == DistributorMembershipStatus.ACTIVE
                && membership.getGrantedAt() != null));
    }

    @Test
    void onlyActiveAuthenticatedAccountsCanCreateBusiness() {
        UUID id = UUID.randomUUID();
        User inactive = new User();
        inactive.setStatus("SUSPENDED");
        when(users.findById(id)).thenReturn(Optional.of(inactive));
        assertThrows(AccessDeniedException.class, () -> service.createBusiness(id, "Distributor"));
        assertThrows(IllegalArgumentException.class, () -> service.createBusiness(id, "  "));
        assertThrows(IllegalArgumentException.class, () -> service.createBusiness(null, "Distributor"));
        verifyNoInteractions(businesses, memberships);
    }
}
