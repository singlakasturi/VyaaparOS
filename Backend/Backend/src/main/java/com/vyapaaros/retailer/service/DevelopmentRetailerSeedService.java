package com.vyapaaros.retailer.service;

import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.entity.RetailerMembership;
import com.vyapaaros.retailer.entity.RetailerMembershipRole;
import com.vyapaaros.retailer.entity.RetailerStatus;
import com.vyapaaros.retailer.repository.RetailerMembershipRepository;
import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.entity.RetailerDistributorRelationship;
import com.vyapaaros.distributor.entity.RetailerDistributorRelationship.RelationshipStatus;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.distributor.repository.RetailerDistributorRelationshipRepository;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.order.dto.OrderItemRequest;
import com.vyapaaros.order.dto.OrderRequest;
import com.vyapaaros.order.service.OrderService;
import com.vyapaaros.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Service
public class DevelopmentRetailerSeedService {

    @PersistenceContext
    private EntityManager entityManager;

    private final RetailerRepository retailerRepository;
    private final RetailerMembershipRepository membershipRepository;
    private final DistributorBusinessRepository distributorBusinesses;
    private final RetailerDistributorRelationshipRepository relationships;
    private final ProductRepository products;
    private final OrderService orders;

    public DevelopmentRetailerSeedService(
        RetailerRepository retailerRepository,
        RetailerMembershipRepository membershipRepository
    ) {
        this(retailerRepository, membershipRepository, null, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DevelopmentRetailerSeedService(RetailerRepository retailerRepository,
        RetailerMembershipRepository membershipRepository, DistributorBusinessRepository distributorBusinesses,
        RetailerDistributorRelationshipRepository relationships, ProductRepository products, OrderService orders) {
        this.retailerRepository = retailerRepository;
        this.membershipRepository = membershipRepository;
        this.distributorBusinesses = distributorBusinesses;
        this.relationships = relationships;
        this.products = products;
        this.orders = orders;
    }

    @Transactional
    public void ensureRetailerMembership(User user, BigDecimal developmentCreditLimit) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("A persisted development user is required.");
        }
        if (developmentCreditLimit == null || developmentCreditLimit.signum() < 0) {
            throw new IllegalArgumentException("Development retailer credit limit must be zero or greater.");
        }

        // Only adjust the retailer seeded for this configured demo identity. Preserve
        // unrelated memberships and existing orders; the fixed demo order is created
        // through OrderService so current pricing, schemes, credit and stock rules apply.
        Retailer retailer = retailerRepository
            .findFirstByContactEmailIgnoreCaseOrderByCreatedAtAsc(user.getEmail())
            .orElse(null);
        List<RetailerMembership> memberships = membershipRepository.findByUserId(user.getId());
        if (!memberships.isEmpty()) {
            if (retailer != null) {
                retailer.setCreditLimit(developmentCreditLimit);
                retailerRepository.save(retailer);
                DistributorBusiness business = ensureDevelopmentDistributorLink(user, retailer);
                ensureDemoOrder(user, retailer, business);
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
        DistributorBusiness business = ensureDevelopmentDistributorLink(user, retailer);
        ensureDemoOrder(user, retailer, business);
    }

    private DistributorBusiness ensureDevelopmentDistributorLink(User user, Retailer retailer) {
        if (distributorBusinesses == null || relationships == null) return null;
        DistributorBusiness business = distributorBusinesses.findAllByCreatedById(user.getId()).stream()
            .findFirst().orElse(null);
        if (business == null) return null;
        var active = relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(
            retailer.getId(), RelationshipStatus.ACTIVE);
        if (active.isPresent()) {
            return active.get().getDistributor().getId().equals(business.getId()) ? business : null;
        }
        RetailerDistributorRelationship relationship = new RetailerDistributorRelationship();
        relationship.setRetailer(retailer);
        relationship.setDistributor(business);
        relationship.setStatus(RelationshipStatus.ACTIVE);
        relationship.setStartedBy(entityManager.getReference(User.class, user.getId()));
        relationship.setStartedAt(Instant.now());
        relationships.save(relationship);
        return business;
    }

    private void ensureDemoOrder(User user, Retailer retailer, DistributorBusiness business) {
        if (products == null || orders == null || business == null) return;
        var biscuit = products.findByDistributorIdAndSkuIgnoreCase(business.getId(), "SKU-BS-075").orElse(null);
        if (biscuit == null || !biscuit.isActive()) return;
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null,
            List.of(new SimpleGrantedAuthority("ROLE_RETAILER")));
        orders.place(authentication, new OrderRequest(retailer.getId(),
            UUID.fromString("24000000-0000-0000-0000-000000000001"),
            "Demo seed: 10 cases Glucose biscuit 75 g (Buy 10, get 1 free)",
            List.of(new OrderItemRequest(biscuit.getId(), 10))));
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
        retailer.setContactPhone("9876543210");
        retailer.setAddress("14 Main Market Road");
        retailer.setCity("Jaipur");
        retailer.setState("Rajasthan");
        retailer.setPincode("302001");
        retailer.setCreditLimit(BigDecimal.ZERO);
        retailer.setOutstandingBalance(BigDecimal.ZERO);
        retailer.setStatus(RetailerStatus.ACTIVE);
        retailer.setCreatedAt(now);
        retailer.setUpdatedAt(now);
        return retailerRepository.save(retailer);
    }
}
