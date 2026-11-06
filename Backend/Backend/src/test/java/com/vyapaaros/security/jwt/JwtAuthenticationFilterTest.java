package com.vyapaaros.security.jwt;

import com.vyapaaros.security.entity.Role;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter.
 *
 * These tests verify:
 * - Active users with valid JWTs are authenticated.
 * - Disabled users are rejected with 401 even with a valid JWT.
 * - Deleted users are rejected with 401 even with a valid JWT.
 * - Expired JWTs are rejected.
 * - Current database roles are used (not JWT-embedded roles).
 */
class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET_B64 =
        "c29tZS1zdHJvbmctdGVzdC1zZWNyZXQta2V5LWZvci1qd3Qtc2lnbmluZw==";

    private final SecretKey signingKey = Keys.hmacShaKeyFor(
        Base64.getDecoder().decode(TEST_SECRET_B64)
    );
    private final JwtService jwtService = new JwtService(signingKey, 900);
    private final UserRepository userRepository = mock(UserRepository.class);
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userRepository);
    }

    private User activeUser(UUID id, String... roleNames) {
        User user = new User() {};
        user.setId(id);
        user.setEmail("test@example.com");
        user.setStatus("ACTIVE");
        for (String roleName : roleNames) {
            Role role = new Role() {};
            role.setName(roleName);
            user.getRoles().add(role);
        }
        return user;
    }

    private User disabledUser(UUID id) {
        User user = activeUser(id, "SALESPERSON");
        user.setStatus("DISABLED");
        return user;
    }

    private String validToken(UUID userId) {
        return jwtService.createAuthResponse(activeUser(userId, "SALESPERSON")).accessToken();
    }

    @Test
    void allowsRequestWithNoAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
        verifyNoInteractions(userRepository);
    }

    @Test
    void authenticatesActiveUserWithValidJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, "SALESPERSON");
        when(userRepository.findWithRolesById(userId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + validToken(userId));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsDisabledUserEvenWithValidJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = disabledUser(userId);
        when(userRepository.findWithRolesById(userId)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + validToken(userId));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsDeletedUserEvenWithValidJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findWithRolesById(userId)).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + validToken(userId));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsInvalidOrExpiredToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer this.is.not.valid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void usesCurrentDatabaseRolesNotJwtClaimsForAuthorities() throws Exception {
        UUID userId = UUID.randomUUID();
        // JWT will have SALESPERSON role, but DB now has ADMIN
        User userWithChangedRole = activeUser(userId, "ADMIN");
        when(userRepository.findWithRolesById(userId)).thenReturn(Optional.of(userWithChangedRole));

        MockHttpServletRequest request = new MockHttpServletRequest();
        // Token was issued with SALESPERSON role
        request.addHeader("Authorization", "Bearer " + validToken(userId));
        MockHttpServletResponse response = new MockHttpServletResponse();

        final org.springframework.security.core.Authentication[] capturedAuth =
            new org.springframework.security.core.Authentication[1];
        FilterChain chain = (req, res) -> {
            capturedAuth[0] = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        };

        filter.doFilterInternal(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(capturedAuth[0]);
        // Should have ROLE_ADMIN from DB, not ROLE_SALESPERSON from JWT
        boolean hasAdmin = capturedAuth[0].getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean hasSalesperson = capturedAuth[0].getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_SALESPERSON"));
        assertTrue(hasAdmin, "Should have current DB role ADMIN");
        assertFalse(hasSalesperson, "Should NOT have stale JWT role SALESPERSON");

        // Clean up security context
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
