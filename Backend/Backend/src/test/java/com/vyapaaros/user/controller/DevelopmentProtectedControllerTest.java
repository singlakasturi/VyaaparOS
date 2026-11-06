package com.vyapaaros.user.controller;

import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DevelopmentProtectedControllerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new DevelopmentProtectedController(userRepository))
            .build();
        userId = UUID.randomUUID();
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/dev/protected-test"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsAuthenticatedIdentityAndAuthorities() throws Exception {
        User user = new User();
        user.setId(userId);
        user.setEmail("retailer@example.test");
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_RETAILER"))
            )
        );

        try {
            mockMvc.perform(get("/api/dev/protected-test"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.email").value("retailer@example.test"))
            .andExpect(jsonPath("$.authorities[0]").value("ROLE_RETAILER"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
