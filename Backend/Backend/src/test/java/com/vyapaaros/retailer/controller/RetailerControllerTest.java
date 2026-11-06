package com.vyapaaros.retailer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyapaaros.retailer.dto.RetailerOnboardingRequest;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.entity.RetailerStatus;
import com.vyapaaros.retailer.service.RetailerOnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RetailerControllerTest {

    private final RetailerOnboardingService onboardingService = mock(
        RetailerOnboardingService.class
    );
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new RetailerController(onboardingService))
            .build();
        userId = UUID.randomUUID();
    }

    @Test
    void createsRetailerFromAuthenticatedPrincipal() throws Exception {
        Retailer retailer = new Retailer();
        retailer.setId(UUID.randomUUID());
        retailer.setBusinessName("Fresh Shop");
        retailer.setContactPhone("12345");
        retailer.setAddress("Main Road");
        retailer.setStatus(RetailerStatus.PENDING);
        when(onboardingService.createRetailer(eq(userId), any(RetailerOnboardingRequest.class)))
            .thenReturn(retailer);

        var response = new RetailerController(onboardingService).createRetailer(
            new RetailerOnboardingRequest("Fresh Shop", "12345", "Main Road"),
            new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of())
        );

        org.junit.jupiter.api.Assertions.assertEquals(201, response.getStatusCode().value());
        verify(onboardingService).createRetailer(
            eq(userId),
            any(RetailerOnboardingRequest.class)
        );
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/retailers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"businessName":"Fresh Shop","contactPhone":"12345","address":"Main Road"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsBlankMandatoryFieldsAndProtectedRequestFields() throws Exception {
        mockMvc.perform(post("/api/retailers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "businessName": " ",
                      "contactPhone": "",
                      "address": " ",
                      "userId": "00000000-0000-0000-0000-000000000000",
                      "contactEmail": "attacker@example.com",
                      "status": "ACTIVE",
                      "creditLimit": 999999
                    }
                    """))
            .andExpect(status().isBadRequest());
    }
}
