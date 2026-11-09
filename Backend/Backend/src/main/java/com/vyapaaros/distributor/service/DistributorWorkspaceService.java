package com.vyapaaros.distributor.service;

import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.dto.DistributorBusinessResponse;
import com.vyapaaros.distributor.dto.DistributorStaffResponse;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.entity.DistributorMembership;
import com.vyapaaros.distributor.entity.DistributorMembershipStatus;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.distributor.repository.DistributorMembershipRepository;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DistributorWorkspaceService {
    private final DistributorBusinessRepository businesses;
    private final DistributorMembershipRepository memberships;
    private final UserRepository users;
    private final DistributorAuthorizationService authorization;

    public DistributorWorkspaceService(
        DistributorBusinessRepository businesses,
        DistributorMembershipRepository memberships,
        UserRepository users,
        DistributorAuthorizationService authorization
    ) {
        this.businesses = businesses;
        this.memberships = memberships;
        this.users = users;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public DistributorBusinessResponse getBusiness(UUID userId) {
        DistributorBusiness business = currentBusiness(userId);
        boolean owner = userId.equals(business.getCreatedBy().getId());
        return DistributorBusinessResponse.from(business, owner);
    }

    @Transactional
    public DistributorBusinessResponse updateBusiness(UUID userId, String businessName) {
        DistributorBusiness business = currentBusiness(userId);
        authorization.requireOriginalCreator(userId, business.getId());
        if (businessName == null || businessName.isBlank() || businessName.trim().length() > 200) {
            throw new IllegalArgumentException("Business name must contain 1 to 200 characters.");
        }
        business.setBusinessName(businessName.trim());
        business.setUpdatedAt(Instant.now());
        return DistributorBusinessResponse.from(businesses.save(business), true);
    }

    @Transactional(readOnly = true)
    public List<DistributorStaffResponse> listStaff(UUID userId) {
        DistributorBusiness business = currentBusiness(userId);
        authorization.requireOriginalCreator(userId, business.getId());
        return memberships.findByDistributorIdAndStatusOrderByGrantedAtAsc(
                business.getId(), DistributorMembershipStatus.ACTIVE).stream()
            .filter(membership -> !userId.equals(membership.getUser().getId()))
            .map(DistributorStaffResponse::from)
            .toList();
    }

    @Transactional
    public DistributorStaffResponse addStaff(UUID userId, String email) {
        DistributorBusiness business = currentBusiness(userId);
        authorization.requireOriginalCreator(userId, business.getId());
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User staff = users.findByEmail(normalizedEmail)
            .filter(user -> "ACTIVE".equals(user.getStatus()))
            .orElseThrow(() -> new ResourceNotFoundException("No active account was found for that email."));

        if (staff.getId().equals(business.getCreatedBy().getId())) {
            throw new ResourceConflictException("The business owner already has owner access.");
        }
        if (memberships.existsByDistributorIdAndUserIdAndStatus(
            business.getId(), staff.getId(), DistributorMembershipStatus.ACTIVE)) {
            throw new ResourceConflictException("This account already has active access to the distributor.");
        }
        if (memberships.existsByUserIdAndStatusAndDistributorIdNot(
            staff.getId(), DistributorMembershipStatus.ACTIVE, business.getId())
            || !businesses.findAllByCreatedById(staff.getId()).isEmpty()) {
            throw new ResourceConflictException("This account is already connected to another distributor business.");
        }

        Instant now = Instant.now();
        DistributorMembership membership = new DistributorMembership();
        membership.setDistributor(business);
        membership.setUser(staff);
        membership.setStatus(DistributorMembershipStatus.ACTIVE);
        membership.setGrantedAt(now);
        try {
            return DistributorStaffResponse.from(memberships.saveAndFlush(membership));
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException("This account already has active access to a distributor business.");
        }
    }

    @Transactional
    public void revokeStaff(UUID userId, UUID staffUserId) {
        DistributorBusiness business = currentBusiness(userId);
        authorization.requireOriginalCreator(userId, business.getId());
        if (staffUserId == null || staffUserId.equals(business.getCreatedBy().getId())) {
            throw new ResourceConflictException("The business owner cannot revoke their own owner access.");
        }
        DistributorMembership membership = memberships
            .findFirstByDistributorIdAndUserIdAndStatusOrderByGrantedAtDesc(
                business.getId(), staffUserId, DistributorMembershipStatus.ACTIVE)
            .orElseThrow(() -> new ResourceNotFoundException("Active staff membership was not found."));
        membership.setStatus(DistributorMembershipStatus.REVOKED);
        membership.setRevokedAt(Instant.now());
        memberships.save(membership);
    }

    private DistributorBusiness currentBusiness(UUID userId) {
        if (userId == null) throw new AccessDeniedException("Authentication required.");
        Set<UUID> accessibleBusinesses = authorization.activeOrderDeskBusinessIds(userId);
        if (accessibleBusinesses.size() != 1) {
            throw new AccessDeniedException("A single current distributor business could not be resolved.");
        }
        UUID businessId = accessibleBusinesses.iterator().next();
        authorization.requireOrderDeskAccess(userId, businessId);
        return businesses.findById(businessId)
            .orElseThrow(() -> new ResourceNotFoundException("Distributor business not found."));
    }
}
