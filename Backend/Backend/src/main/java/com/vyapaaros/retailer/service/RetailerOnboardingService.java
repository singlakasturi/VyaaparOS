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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class RetailerOnboardingService {

    private final UserRepository userRepository;
    private final RetailerRepository retailerRepository;
    private final RetailerMembershipRepository membershipRepository;

    public RetailerOnboardingService(
        UserRepository userRepository,
        RetailerRepository retailerRepository,
        RetailerMembershipRepository membershipRepository
    ) {
        this.userRepository = userRepository;
        this.retailerRepository = retailerRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public Retailer createRetailer(
        UUID authenticatedUserId,
        RetailerOnboardingRequest request
    ) {
        User user = userRepository.findById(authenticatedUserId)
            .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
            .orElseThrow(() -> new AccessDeniedException("User is not eligible"));

        Instant now = Instant.now();
        Retailer retailer = new Retailer();
        retailer.setBusinessName(request.businessName().trim());
        retailer.setContactPhone(request.contactPhone().trim());
        retailer.setAddress(request.address().trim());
        retailer.setContactEmail(user.getEmail());
        retailer.setOwnerName(normalizeOptional(user.getFullName()));
        retailer.setCreditLimit(BigDecimal.ZERO);
        retailer.setOutstandingBalance(BigDecimal.ZERO);
        retailer.setStatus(RetailerStatus.PENDING);
        retailer.setCreatedAt(now);
        retailer.setUpdatedAt(now);

        Retailer savedRetailer = retailerRepository.save(retailer);

        RetailerMembership membership = new RetailerMembership();
        membership.setUser(user);
        membership.setRetailer(savedRetailer);
        membership.setRole(RetailerMembershipRole.RETAILER_OWNER);
        membership.setCreatedAt(now);
        membershipRepository.save(membership);

        return savedRetailer;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
