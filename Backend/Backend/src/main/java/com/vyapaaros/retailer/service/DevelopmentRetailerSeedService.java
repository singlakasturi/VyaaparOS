package com.vyapaaros.retailer.service;

import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.entity.RetailerMembership;
import com.vyapaaros.retailer.entity.RetailerMembershipRole;
import com.vyapaaros.retailer.entity.RetailerStatus;
import com.vyapaaros.retailer.repository.RetailerMembershipRepository;
import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class DevelopmentRetailerSeedService {

    @PersistenceContext
    private EntityManager entityManager;

    private final RetailerRepository retailerRepository;
    private final RetailerMembershipRepository membershipRepository;

    public DevelopmentRetailerSeedService(
        RetailerRepository retailerRepository,
        RetailerMembershipRepository membershipRepository
    ) {
        this.retailerRepository = retailerRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public void ensureRetailerMembership(User user, BigDecimal developmentCreditLimit) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("A persisted development user is required.");
        }
        if (developmentCreditLimit == null || developmentCreditLimit.signum() < 0) {
            throw new IllegalArgumentException("Development retailer credit limit must be zero or greater.");
        }

        // Only adjust the retailer seeded for this configured test identity. Preserve
        // unrelated memberships and never rewrite balances or existing orders.
        Retailer retailer = retailerRepository
            .findFirstByContactEmailIgnoreCaseOrderByCreatedAtAsc(user.getEmail())
            .orElse(null);
        List<RetailerMembership> memberships = membershipRepository.findByUserId(user.getId());
        if (!memberships.isEmpty()) {
            if (retailer != null) {
                retailer.setCreditLimit(developmentCreditLimit);
                retailerRepository.save(retailer);
            }
            return;
        }

        if (retailer == null) retailer = createDevelopmentRetailer(user);
        retailer.setCreditLimit(developmentCreditLimit);
        retailer = retailerRepository.save(retailer);

        if (membershipRepository.findByUserIdAndRetailerId(user.getId(), retailer.getId())
            .isPresent()) {
            return;
        }

        RetailerMembershipRole role = membershipRepository
            .findByRetailerIdAndRole(retailer.getId(), RetailerMembershipRole.RETAILER_OWNER)
            .filter(owner -> !user.getId().equals(owner.getUser().getId()))
            .isPresent()
                ? RetailerMembershipRole.RETAILER_STAFF
                : RetailerMembershipRole.RETAILER_OWNER;

        RetailerMembership membership = new RetailerMembership();
        // A CommandLineRunner may pass an entity returned by repository save/merge.
        // Resolve its identity in this transaction so membership persistence always
        // points at a managed User instance.
        membership.setUser(entityManager.getReference(User.class, user.getId()));
        membership.setRetailer(retailer);
        membership.setRole(role);
        membership.setCreatedAt(Instant.now());
        membershipRepository.save(membership);
    }

    private Retailer createDevelopmentRetailer(User user) {
        Instant now = Instant.now();
        Retailer retailer = new Retailer();
        String ownerName = user.getFullName() == null || user.getFullName().isBlank()
            ? "VyapaarOS Test Retailer"
            : user.getFullName().trim();
        retailer.setBusinessName(ownerName + " Store");
        retailer.setOwnerName(ownerName);
        retailer.setContactEmail(user.getEmail());
        retailer.setContactPhone("0000000000");
        retailer.setAddress("Development seed address");
        retailer.setCreditLimit(BigDecimal.ZERO);
        retailer.setOutstandingBalance(BigDecimal.ZERO);
        retailer.setStatus(RetailerStatus.ACTIVE);
        retailer.setCreatedAt(now);
        retailer.setUpdatedAt(now);
        return retailerRepository.save(retailer);
    }
}
