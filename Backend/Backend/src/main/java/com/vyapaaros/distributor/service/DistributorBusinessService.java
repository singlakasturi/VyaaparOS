package com.vyapaaros.distributor.service;

import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.entity.DistributorMembership;
import com.vyapaaros.distributor.entity.DistributorMembershipStatus;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.distributor.repository.DistributorMembershipRepository;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class DistributorBusinessService {
    private final DistributorBusinessRepository businesses;
    private final DistributorMembershipRepository memberships;
    private final UserRepository users;

    public DistributorBusinessService(DistributorBusinessRepository businesses,
        DistributorMembershipRepository memberships, UserRepository users) {
        this.businesses = businesses;
        this.memberships = memberships;
        this.users = users;
    }

    @Transactional
    public DistributorBusiness createBusiness(UUID authenticatedUserId, String businessName) {
        if (authenticatedUserId == null || businessName == null || businessName.isBlank()
            || businessName.trim().length() > 200) {
            throw new IllegalArgumentException("An authenticated creator and business name of 1 to 200 characters are required.");
        }
        User creator = users.findById(authenticatedUserId)
            .filter(user -> "ACTIVE".equals(user.getStatus()))
            .orElseThrow(() -> new AccessDeniedException("Active user account required."));

        Instant now = Instant.now();
        DistributorBusiness business = new DistributorBusiness();
        business.setBusinessName(businessName.trim());
        business.setCreatedBy(creator);
        business.setCreatedAt(now);
        business.setUpdatedAt(now);
        DistributorBusiness saved = businesses.save(business);

        DistributorMembership creatorMembership = new DistributorMembership();
        creatorMembership.setDistributor(saved);
        creatorMembership.setUser(creator);
        creatorMembership.setStatus(DistributorMembershipStatus.ACTIVE);
        creatorMembership.setGrantedAt(now);
        memberships.save(creatorMembership);
        return saved;
    }
}
