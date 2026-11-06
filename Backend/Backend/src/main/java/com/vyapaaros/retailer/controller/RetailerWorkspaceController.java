package com.vyapaaros.retailer.controller;

import com.vyapaaros.retailer.dto.RetailerResponse;
import com.vyapaaros.retailer.repository.RetailerMembershipRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/retailers")
public class RetailerWorkspaceController {

    private final RetailerMembershipRepository memberships;

    public RetailerWorkspaceController(RetailerMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @GetMapping("/mine")
    public List<RetailerResponse> getMyRetailers(Authentication authentication) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new AccessDeniedException("Authentication required.");
        }
        return memberships.findByUserId(userId).stream()
            .map(membership -> RetailerResponse.from(membership.getRetailer()))
            .toList();
    }
}
