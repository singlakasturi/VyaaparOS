package com.vyapaaros.retailer.service;

import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.entity.RetailerMembership;
import com.vyapaaros.retailer.repository.RetailerMembershipRepository;
import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DevelopmentRetailerSeedServiceTest {

    @Test
    void repeatableSeedSetsConfiguredLimitWithoutChangingBalanceOrOrders() {
        RetailerRepository retailerRepository = mock(RetailerRepository.class);
        RetailerMembershipRepository membershipRepository = mock(RetailerMembershipRepository.class);
        DevelopmentRetailerSeedService service = new DevelopmentRetailerSeedService(
            retailerRepository,
            membershipRepository
        );
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("retailer@example.test");
        Retailer retailer = new Retailer();
        retailer.setCreditLimit(BigDecimal.ZERO);
        retailer.setOutstandingBalance(new BigDecimal("125.00"));
        when(retailerRepository.findFirstByContactEmailIgnoreCaseOrderByCreatedAtAsc(user.getEmail()))
            .thenReturn(Optional.of(retailer));
        RetailerMembership membership = new RetailerMembership();
        when(membershipRepository.findByUserId(user.getId())).thenReturn(List.of(membership));

        service.ensureRetailerMembership(user, new BigDecimal("1000.00"));
        service.ensureRetailerMembership(user, new BigDecimal("1000.00"));

        assertEquals(new BigDecimal("1000.00"), retailer.getCreditLimit());
        assertEquals(new BigDecimal("125.00"), retailer.getOutstandingBalance());
        verify(membershipRepository, times(2)).findByUserId(user.getId());
        verify(retailerRepository, times(2)).save(retailer);
        verifyNoMoreInteractions(membershipRepository);
    }
}
