package com.vyapaaros.retailer.service;

import com.vyapaaros.order.service.OrderScopeService;
import com.vyapaaros.retailer.repository.RetailerMembershipRepository;
import com.vyapaaros.retailer.repository.RetailerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetailerOrderMembershipAuthorizationTest {
    private final RetailerRepository retailers = mock(RetailerRepository.class);
    private final RetailerMembershipRepository memberships = mock(RetailerMembershipRepository.class);
    private final RetailerAuthorizationService authorization = new RetailerAuthorizationService(memberships);
    private final OrderScopeService orderScope = new OrderScopeService(retailers, authorization);
    private final UUID userId = UUID.randomUUID();
    private final UUID retailerId = UUID.randomUUID();

    @Test
    void retailerRoleAndPersistedMembershipAllowAccess() {
        when(retailers.existsById(retailerId)).thenReturn(true);
        when(memberships.findByUserIdAndRetailerId(userId, retailerId)).thenReturn(Optional.of(new com.vyapaaros.retailer.entity.RetailerMembership()));
        var auth = auth("ROLE_RETAILER");

        assertDoesNotThrow(() -> orderScope.require(userId, retailerId, auth));
    }

    @Test
    void legacyRolesAndMissingMembershipCannotGrantRetailerAccess() {
        when(retailers.existsById(retailerId)).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> orderScope.require(userId, retailerId, auth("ROLE_SALESPERSON")));
        assertThrows(AccessDeniedException.class, () -> orderScope.require(userId, retailerId, auth("ROLE_ADMIN")));
        assertThrows(AccessDeniedException.class, () -> orderScope.require(userId, retailerId, auth("ROLE_RETAILER")));
    }

    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority(role)));
    }
}
