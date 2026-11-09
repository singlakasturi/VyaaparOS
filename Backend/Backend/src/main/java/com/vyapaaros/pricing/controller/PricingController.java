package com.vyapaaros.pricing.controller;

import com.vyapaaros.pricing.dto.PriceRequest;
import com.vyapaaros.pricing.dto.PriceResponse;
import com.vyapaaros.pricing.service.PriceManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.UUID;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PriceManagementService priceManagementService;

    public PricingController(PriceManagementService priceManagementService) {
        this.priceManagementService = priceManagementService;
    }

    @GetMapping("/me/defaults")
    public ResponseEntity<java.util.List<com.vyapaaros.pricing.dto.ManagedPriceResponse>> listDefaults(Authentication authentication) {
        return ResponseEntity.ok(priceManagementService.listDefaults(userId(authentication)));
    }

    @GetMapping("/me/products/{productId}/default")
    public ResponseEntity<com.vyapaaros.pricing.dto.ManagedPriceResponse> getDefault(@PathVariable UUID productId, Authentication authentication) {
        return ResponseEntity.ok(priceManagementService.getDefault(userId(authentication), productId));
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UUID id))
            throw new AuthenticationCredentialsNotFoundException("Authentication required.");
        return id;
    }

    @PutMapping("/products/{productId}/default")
    public ResponseEntity<PriceResponse> upsertDefault(
        @PathVariable UUID productId,
        @Valid @RequestBody PriceRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(PriceResponse.from(
            priceManagementService.upsertDefault(authentication, productId, request)
        ));
    }

    @DeleteMapping("/products/{productId}/default")
    public ResponseEntity<Void> deactivateDefault(@PathVariable UUID productId, Authentication authentication) {
        priceManagementService.deactivateDefault(authentication, productId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/retailers/{retailerId}/products/{productId}")
    public ResponseEntity<PriceResponse> upsertOverride(
        @PathVariable UUID retailerId,
        @PathVariable UUID productId,
        @Valid @RequestBody PriceRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(PriceResponse.from(
            priceManagementService.upsertOverride(
                authentication,
                retailerId,
                productId,
                request
            )
        ));
    }

    @DeleteMapping("/retailers/{retailerId}/products/{productId}")
    public ResponseEntity<Void> deactivateOverride(
        @PathVariable UUID retailerId,
        @PathVariable UUID productId,
        Authentication authentication
    ) {
        priceManagementService.deactivateOverride(
            authentication,
            retailerId,
            productId
        );
        return ResponseEntity.noContent().build();
    }
}
