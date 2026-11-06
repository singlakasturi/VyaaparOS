package com.vyapaaros.user;

import com.vyapaaros.security.entity.Role;
import com.vyapaaros.security.repository.RoleRepository;
import com.vyapaaros.retailer.service.DevelopmentRetailerSeedService;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DevelopmentUserSeederTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final DevelopmentRetailerSeedService retailerSeedService =
        mock(DevelopmentRetailerSeedService.class);
    private final Role retailerRole = mock(Role.class);
    private DevelopmentUserSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DevelopmentUserSeeder(
            userRepository,
            roleRepository,
            retailerSeedService,
            true,
            "Kasturisingla0@gmail.com",
            "VyapaarOS Test Retailer",
            new BigDecimal("1000.00")
        );
    }

    @Test
    void persistsNewUserWithRetailerRole() {
        when(userRepository.findByEmail("kasturisingla0@gmail.com"))
            .thenReturn(Optional.empty());
        when(roleRepository.findByName("RETAILER")).thenReturn(Optional.of(retailerRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        seeder.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(true, hasExpectedUser(userCaptor.getValue(), retailerRole));
        verify(retailerSeedService).ensureRetailerMembership(userCaptor.getValue(), new BigDecimal("1000.00"));
    }

    @Test
    void ensuresMembershipForExistingUserOnRepeatedExecution() {
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        when(userRepository.findByEmail("kasturisingla0@gmail.com"))
            .thenReturn(Optional.of(existingUser));

        seeder.run();
        seeder.run();

        verify(userRepository, times(2)).findByEmail("kasturisingla0@gmail.com");
        verify(retailerSeedService, times(2)).ensureRetailerMembership(existingUser, new BigDecimal("1000.00"));
        verifyNoInteractions(roleRepository);
    }

    private boolean hasExpectedUser(User user, Role expectedRole) {
        return "kasturisingla0@gmail.com".equals(user.getEmail())
            && "VyapaarOS Test Retailer".equals(user.getFullName())
            && "ACTIVE".equals(user.getStatus())
            && user.getCreatedAt() != null
            && user.getUpdatedAt() != null
            && user.getRoles().size() == 1
            && user.getRoles().get(0) == expectedRole;
    }
}
