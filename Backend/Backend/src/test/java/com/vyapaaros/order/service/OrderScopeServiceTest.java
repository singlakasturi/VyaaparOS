package com.vyapaaros.order.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.retailer.service.RetailerAuthorizationService;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class OrderScopeServiceTest {
    private final RetailerRepository retailers = mock(RetailerRepository.class);
    private final RetailerAuthorizationService authorization = mock(RetailerAuthorizationService.class);
    private final OrderScopeService service = new OrderScopeService(retailers, authorization);
    private final UUID userId = UUID.randomUUID();
    private final UUID retailerId = UUID.randomUUID();

    @Test
    void retailerUsesMembershipScope() {
        when(retailers.existsById(retailerId)).thenReturn(true);
        service.require(userId, retailerId, auth("ROLE_RETAILER"));
        verify(authorization).requireMembership(userId, retailerId);
    }

    @Test
    void retailerOrderScopeAlsoRequiresCurrentDistributorRelationship() {
        when(retailers.existsById(retailerId)).thenReturn(true);
        DistributorAuthorizationService businessAuthorization = mock(DistributorAuthorizationService.class);
        OrderScopeService scopedService = new OrderScopeService(retailers, authorization, businessAuthorization);
        scopedService.require(userId, retailerId, auth("ROLE_RETAILER"));
        verify(authorization).requireMembership(userId, retailerId);
        verify(businessAuthorization).requireCurrentDistributorForRetailer(retailerId);
    }

    @Test
    void historicalOrdersRemainVisibleToRetailerMemberAfterRelationshipEnds() {
        when(retailers.existsById(retailerId)).thenReturn(true);
        DistributorAuthorizationService businessAuthorization = mock(DistributorAuthorizationService.class);
        OrderScopeService scopedService = new OrderScopeService(retailers, authorization, businessAuthorization);

        scopedService.requireHistoricalOrderAccess(userId, retailerId, auth("ROLE_RETAILER"));

        verify(authorization).requireMembership(userId, retailerId);
        verifyNoInteractions(businessAuthorization);
    }

    @Test
    void endedDistributorRelationshipBlocksRetailerOperationsDespiteMembership() {
        when(retailers.existsById(retailerId)).thenReturn(true);
        DistributorAuthorizationService businessAuthorization=mock(DistributorAuthorizationService.class);
        doThrow(new AccessDeniedException("retailer has no active distributor"))
            .when(businessAuthorization).requireCurrentDistributorForRetailer(retailerId);
        OrderScopeService scopedService=new OrderScopeService(retailers,authorization,businessAuthorization);
        assertThrows(AccessDeniedException.class,()->scopedService.require(userId,retailerId,auth("ROLE_RETAILER")));
        verify(authorization).requireMembership(userId,retailerId);
    }

    @Test
    void legacySalespersonRoleCannotAccessRetailerOrderScope() {
        when(retailers.existsById(retailerId)).thenReturn(true);
        assertThrows(AccessDeniedException.class,
            () -> service.require(userId, retailerId, auth("ROLE_SALESPERSON")));
        verifyNoInteractions(authorization);
    }

    @Test
    void ordinaryUserCannotAccessOrderScope() {
        when(retailers.existsById(retailerId)).thenReturn(true);
        assertThrows(AccessDeniedException.class,
            () -> service.require(userId, retailerId, auth("ROLE_UNKNOWN")));
    }

    @Test
    void legacyGlobalOrderDeskAndAdminAuthoritiesDoNotGrantRetailerOrderAccess() {
        when(retailers.existsById(retailerId)).thenReturn(true);
        assertThrows(AccessDeniedException.class,
            () -> service.require(userId, retailerId, auth("ROLE_ADMIN")));
        assertThrows(AccessDeniedException.class,
            () -> service.require(userId, retailerId, auth("ROLE_ORDER_DESK")));
        verifyNoInteractions(authorization);
    }

    @Test
    void missingRetailerIsRejected() {
        when(retailers.existsById(retailerId)).thenReturn(false);
        assertThrows(RuntimeException.class,
            () -> service.require(userId, retailerId, auth("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(userId, null,
            List.of(new SimpleGrantedAuthority(role)));
    }
}
