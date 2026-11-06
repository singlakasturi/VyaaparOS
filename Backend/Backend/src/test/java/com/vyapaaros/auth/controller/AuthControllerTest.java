package com.vyapaaros.auth.controller;

import com.vyapaaros.auth.dto.AuthResponseDto;
import com.vyapaaros.auth.service.OtpService;
import com.vyapaaros.common.exception.GlobalExceptionHandler;
import com.vyapaaros.security.entity.Role;
import com.vyapaaros.security.jwt.JwtService;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.jpa.repository.EntityGraph;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.lang.reflect.Method;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private final OtpService otpService = mock(OtpService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SecretKey signingKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
        Base64.getDecoder().decode(
            "c29tZS1zdHJvbmctdGVzdC1zZWNyZXQta2V5LWZvci1qd3Qtc2lnbmluZw=="
        )
    );
    private final JwtService jwtService = new JwtService(signingKey, 900);
    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        user = new UserForTest().user;
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AuthController(otpService, userRepository, jwtService)
            )
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void rejectsInvalidOtpRequest() throws Exception {
        mockMvc.perform(post("/api/auth/otp/request")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void sendsGenericResponseForEligibleUser() throws Exception {
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/auth/otp/request")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"buyer@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value(
                "If the email is eligible, a verification code has been sent."
            ));

        verify(otpService).requestOtp("buyer@example.com");
    }

    @Test
    void rejectsInvalidOtpWithoutIssuingJwt() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(otpService.verifyOtp("buyer@example.com", "000000")).thenReturn(false);

        mockMvc.perform(post("/api/auth/otp/verify")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"buyer@example.com\",\"otp\":\"000000\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(
                "The email or verification code is invalid."
            ));
    }

    @Test
    void returnsJwtWithDatabaseRolesAfterSuccessfulVerification() throws Exception {
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(user));
        when(otpService.verifyOtp("buyer@example.com", "123456")).thenReturn(true);

        String response = mockMvc.perform(post("/api/auth/otp/verify")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"buyer@example.com\",\"otp\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresInSeconds").value(900))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String token = response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        Claims claims = jwtService.parse(token);
        assertEquals(user.getId().toString(), claims.getSubject());
        assertTrue(claims.get("roles", java.util.List.class).contains("RETAILER"));
    }

    @Test
    void loadsUserRolesWithUserBeforeJwtResponseCreation() throws Exception {
        Method findByEmail = UserRepository.class.getMethod("findByEmail", String.class);
        EntityGraph entityGraph = findByEmail.getAnnotation(EntityGraph.class);

        assertTrue(entityGraph != null);
        assertEquals("roles", entityGraph.attributePaths()[0]);
    }

    private static class UserForTest {
        private final User user = createUser();

        private static User createUser() {
            User user = new UserReflectionAccess();
            user.setId(UUID.randomUUID());
            user.setEmail("buyer@example.com");
            user.setFullName("Buyer");
            user.setStatus("ACTIVE");

            Role role = new RoleReflectionAccess();
            role.setName("RETAILER");
            user.getRoles().add(role);
            return user;
        }
    }

    private static class UserReflectionAccess extends User {
    }

    private static class RoleReflectionAccess extends Role {
    }
}
