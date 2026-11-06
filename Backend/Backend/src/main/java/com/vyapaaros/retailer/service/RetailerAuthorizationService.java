package com.vyapaaros.retailer.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.vyapaaros.retailer.entity.RetailerMembership;
import com.vyapaaros.retailer.entity.RetailerMembershipRole;
import com.vyapaaros.retailer.repository.RetailerMembershipRepository;

@Service
public class RetailerAuthorizationService {

    private final RetailerMembershipRepository membershipRepository;
    public RetailerAuthorizationService(RetailerMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public RetailerMembership requireMembership(UUID userId, UUID retailerId) {
        if (userId == null || retailerId == null) {
            throw denied();
        }
        return membershipRepository.findByUserIdAndRetailerId(userId, retailerId)
            .orElseThrow(() -> denied());
    }

    public RetailerMembership requireOwner(UUID userId, UUID retailerId) {
        RetailerMembership membership = requireMembership(userId, retailerId);
        requireRole(membership, RetailerMembershipRole.RETAILER_OWNER);
        return membership;
    }

    public RetailerMembership requireManagerOrOwner(UUID userId, UUID retailerId) {
        RetailerMembership membership = requireMembership(userId, retailerId);
        requireRole(
            membership,
            RetailerMembershipRole.RETAILER_OWNER,
            RetailerMembershipRole.RETAILER_MANAGER
        );
        return membership;
    }

    public RetailerMembership requireStaffOrAbove(UUID userId, UUID retailerId) {
        RetailerMembership membership = requireMembership(userId, retailerId);
        requireRole(
            membership,
            RetailerMembershipRole.RETAILER_OWNER,
            RetailerMembershipRole.RETAILER_MANAGER,
            RetailerMembershipRole.RETAILER_STAFF
        );
        return membership;
    }

    private void requireRole(
        RetailerMembership membership,
        RetailerMembershipRole... allowedRoles
    ) {
        if (membership.getRole() == null) {
            throw denied();
        }
        for (RetailerMembershipRole allowedRole : allowedRoles) {
            if (membership.getRole() == allowedRole) {
                return;
            }
        }
        throw denied();
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Retailer access denied.");
    }
}
