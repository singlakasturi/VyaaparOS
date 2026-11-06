package com.vyapaaros.retailer.service;

import com.vyapaaros.retailer.dto.RetailerOnboardingRequest;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.entity.RetailerMembership;
import com.vyapaaros.retailer.entity.RetailerMembershipRole;
import com.vyapaaros.retailer.entity.RetailerStatus;
import com.vyapaaros.retailer.repository.RetailerMembershipRepository;
import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetailerOnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RetailerRepository retailerRepository;

    @Mock
    private RetailerMembershipRepository membershipRepository;

    private RetailerOnboardingService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new RetailerOnboardingService(
            userRepository,
            retailerRepository,
            membershipRepository
        );
        userId = UUID.randomUUID();
    }

    @Test
    void createsRetailerWithAuthenticatedUserDetailsAndOwnerMembership() {
        User user = activeUser("owner@example.com", "  Owner Name ");
        Retailer savedRetailer = new Retailer();
        savedRetailer.setId(UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(retailerRepository.save(any(Retailer.class))).thenReturn(savedRetailer);

        Retailer result = service.createRetailer(
            userId,
            new RetailerOnboardingRequest("  Fresh Shop ", " 12345 ", " Main Road ")
        );

        assertEquals(savedRetailer, result);
        ArgumentCaptor<Retailer> retailerCaptor = ArgumentCaptor.forClass(Retailer.class);
        verify(retailerRepository).save(retailerCaptor.capture());
        Retailer retailer = retailerCaptor.getValue();
        assertEquals("Fresh Shop", retailer.getBusinessName());
        assertEquals("12345", retailer.getContactPhone());
        assertEquals("Main Road", retailer.getAddress());
        assertEquals("owner@example.com", retailer.getContactEmail());
        assertEquals("Owner Name", retailer.getOwnerName());
        assertEquals(BigDecimal.ZERO, retailer.getCreditLimit());
        assertEquals(BigDecimal.ZERO, retailer.getOutstandingBalance());
        assertEquals(RetailerStatus.PENDING, retailer.getStatus());

        ArgumentCaptor<RetailerMembership> membershipCaptor =
            ArgumentCaptor.forClass(RetailerMembership.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        RetailerMembership membership = membershipCaptor.getValue();
        assertEquals(user, membership.getUser());
        assertEquals(savedRetailer, membership.getRetailer());
        assertEquals(RetailerMembershipRole.RETAILER_OWNER, membership.getRole());
    }

    @Test
    void allowsMissingFullNameWithoutPlaceholder() {
        User user = activeUser("owner@example.com", null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(retailerRepository.save(any(Retailer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Retailer retailer = service.createRetailer(
            userId,
            new RetailerOnboardingRequest("Fresh Shop", "12345", "Main Road")
        );

        assertNull(retailer.getOwnerName());
    }

    @Test
    void rejectsMissingOrIneligibleUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThrows(
            AccessDeniedException.class,
            () -> service.createRetailer(
                userId,
                new RetailerOnboardingRequest("Fresh Shop", "12345", "Main Road")
            )
        );

        User inactive = activeUser("owner@example.com", "Owner");
        inactive.setStatus("PENDING");
        when(userRepository.findById(userId)).thenReturn(Optional.of(inactive));
        assertThrows(
            AccessDeniedException.class,
            () -> service.createRetailer(
                userId,
                new RetailerOnboardingRequest("Fresh Shop", "12345", "Main Road")
            )
        );
    }

    @Test
    void propagatesMembershipFailureForTransactionalRollback() {
        User user = activeUser("owner@example.com", "Owner");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(retailerRepository.save(any(Retailer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataIntegrityViolationException("membership failure"))
            .when(membershipRepository)
            .save(any(RetailerMembership.class));

        assertThrows(
            DataIntegrityViolationException.class,
            () -> service.createRetailer(
                userId,
                new RetailerOnboardingRequest("Fresh Shop", "12345", "Main Road")
            )
        );
        verify(retailerRepository).save(any(Retailer.class));
        verify(membershipRepository).save(any(RetailerMembership.class));
    }

    private User activeUser(String email, String fullName) {
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setStatus("ACTIVE");
        return user;
    }
}
