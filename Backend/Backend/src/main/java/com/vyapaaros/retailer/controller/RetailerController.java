package com.vyapaaros.retailer.controller;

import com.vyapaaros.retailer.dto.RetailerOnboardingRequest;
import com.vyapaaros.retailer.dto.RetailerResponse;
import com.vyapaaros.retailer.service.RetailerOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/retailers")
public class RetailerController {

    private final RetailerOnboardingService onboardingService;

    public RetailerController(RetailerOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping
    public ResponseEntity<RetailerResponse> createRetailer(
        @Valid @RequestBody RetailerOnboardingRequest request,
        Authentication authentication
    ) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof UUID userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        RetailerResponse response = RetailerResponse.from(
            onboardingService.createRetailer(userId, request)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
