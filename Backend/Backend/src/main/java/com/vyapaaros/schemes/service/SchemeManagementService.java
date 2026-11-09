package com.vyapaaros.schemes.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.entity.ProductCategory;
import com.vyapaaros.catalog.repository.ProductCategoryRepository;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.schemes.dto.SchemeRequest;
import com.vyapaaros.schemes.entity.*;
import com.vyapaaros.schemes.repository.ProductSchemeRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchemeManagementService {
    private final ProductSchemeRepository repository;
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final RetailerRepository retailers;
    private final DistributorAuthorizationService authorization;
    private final DistributorBusinessRepository businesses;

    public SchemeManagementService(ProductSchemeRepository repository, ProductRepository products, RetailerRepository retailers) {
        this(repository, products, null, retailers, null, null);
    }

    @Autowired
    public SchemeManagementService(ProductSchemeRepository repository, ProductRepository products,
        ProductCategoryRepository categories, RetailerRepository retailers, DistributorAuthorizationService authorization,
        DistributorBusinessRepository businesses) {
        this.repository = repository; this.products = products; this.categories = categories;
        this.retailers = retailers; this.authorization = authorization; this.businesses = businesses;
    }

    @Transactional
    public ProductScheme create(Authentication auth, SchemeRequest request) {
        UUID businessId = businessId(auth);
        requireManagement(auth, businessId);
        Product product = request.productId() == null ? null : product(request.productId(), businessId);
        ProductCategory category = request.categoryId() == null ? null : category(request.categoryId(), businessId);
        validateApplicability(product, category);
        authorizeRetailer(businessId, request.retailerId());
        ProductScheme scheme = new ProductScheme();
        apply(scheme, request, businessId, product, category);
        scheme.setActive(true);
        return repository.save(scheme);
    }

    @Transactional
    public ProductScheme update(Authentication auth, UUID id, SchemeRequest request) {
        ProductScheme scheme = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Scheme not found."));
        UUID businessId = scheme.getDistributor().getId();
        requireManagement(auth, businessId);
        boolean wasActive = scheme.isActive();
        Product product = request.productId() == null ? null : product(request.productId(), businessId);
        ProductCategory category = request.categoryId() == null ? null : category(request.categoryId(), businessId);
        validateApplicability(product, category);
        authorizeRetailer(businessId, request.retailerId());
        apply(scheme, request, businessId, product, category);
        scheme.setActive(wasActive);
        scheme.setUpdatedAt(Instant.now());
        return repository.save(scheme);
    }

    @Transactional
    public void deactivate(Authentication auth, UUID id) {
        ProductScheme scheme = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Scheme not found."));
        requireManagement(auth, scheme.getDistributor().getId());
        scheme.setActive(false); scheme.setUpdatedAt(Instant.now()); repository.save(scheme);
    }

    @Transactional(readOnly = true)
    public java.util.List<ProductScheme> list(Authentication auth, UUID distributorId,
        UUID productId, UUID retailerId, Boolean active) {
        requireManagement(auth, distributorId);
        authorizeRetailer(distributorId, retailerId);
        return repository.searchByDistributor(distributorId, productId, null, retailerId, active);
    }

    @Transactional(readOnly = true)
    public java.util.List<ProductScheme> listManaged(Authentication auth, String query, Boolean active) {
        UUID id = businessId(auth); requireManagement(auth, id);
        return repository.searchManaged(id, normalize(query), active);
    }

    @Transactional(readOnly = true)
    public java.util.List<ProductScheme> retailerSchemes(Authentication auth, UUID retailerId) {
        if (authorization == null || auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UUID userId))
            throw new AccessDeniedException("Scheme access denied.");
        UUID distributorId = authorization.requireRetailerAccess(userId, retailerId);
        Instant now = Instant.now();
        return repository.searchManaged(distributorId, null, true).stream()
            .filter(s -> (s.getStartsAt() == null || !s.getStartsAt().isAfter(now))
                && (s.getEndsAt() == null || s.getEndsAt().isAfter(now))
                && (s.getRetailer() == null || s.getRetailer().getId().equals(retailerId)))
            .toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<ProductScheme> retailerApplicable(Authentication auth, UUID retailerId) {
        if (authorization == null || auth == null || !(auth.getPrincipal() instanceof UUID userId))
            throw new AccessDeniedException("Scheme access denied.");
        UUID distributorId = authorization.requireRetailerAccess(userId, retailerId);
        Instant at = Instant.now();
        return repository.searchManaged(distributorId, null, true).stream()
            .filter(s -> (s.getStartsAt() == null || !s.getStartsAt().isAfter(at))
                && (s.getEndsAt() == null || s.getEndsAt().isAfter(at)))
            .filter(s -> s.getRetailer() == null || s.getRetailer().getId().equals(retailerId))
            .filter(s -> s.getProduct() == null || products.findByIdAndDistributorIdAndActiveTrue(s.getProduct().getId(), distributorId).isPresent())
            .filter(s -> s.getCategory() == null || categories.findByIdAndDistributorIdAndActiveTrue(s.getCategory().getId(), distributorId).isPresent())
            .toList();
    }


    @Transactional(readOnly = true)
    public java.util.List<ProductScheme> applicableRetailerProduct(Authentication auth, UUID retailerId, UUID productId) {
        if (authorization == null || auth == null || !(auth.getPrincipal() instanceof UUID userId))
            throw new AccessDeniedException("Scheme access denied.");
        UUID distributorId = authorization.requireRetailerAccess(userId, retailerId);
        Product product = products.findByIdAndDistributorIdAndActiveTrue(productId, distributorId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found for this distributor."));
        return repository.findApplicable(java.util.List.of(productId), retailerId, distributorId, Instant.now())
            .stream().filter(s -> s.getProduct() == null || s.getProduct().getId().equals(productId)
                || s.getCategory() == null || s.getCategory().getId().equals(product.getCategory().getId())).toList();
    }

    @Transactional
    public ProductScheme setActive(Authentication auth, UUID id, boolean active) {
        ProductScheme scheme = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Scheme not found."));
        requireManagement(auth, scheme.getDistributor().getId());
        scheme.setActive(active); scheme.setUpdatedAt(Instant.now()); return repository.save(scheme);
    }

    private Product product(UUID productId, UUID distributorId) {
        return products.findByIdAndDistributorId(productId, distributorId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found for this distributor."));
    }
    private ProductCategory category(UUID categoryId, UUID distributorId) {
        return categories.findByIdAndDistributorId(categoryId, distributorId)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found for this distributor."));
    }
    private void validateApplicability(Product product, ProductCategory category) {
        if (product != null && category != null) throw new IllegalArgumentException("Choose product or category applicability, not both.");
    }

    private void authorizeRetailer(UUID distributorId, UUID retailerId) {
        if (retailerId != null) authorization.requireActiveRetailerLink(retailerId, distributorId);
    }

    private void apply(ProductScheme scheme, SchemeRequest request, UUID distributorId, Product product, ProductCategory category) {
        if (request.schemeType() == SchemeType.BUY_X_GET_Y_FREE
                && (request.buyQuantity() == null || request.freeQuantity() == null || request.buyQuantity() <= 0 || request.freeQuantity() <= 0 || request.minimumQuantity() != null || request.discountPercentage() != null)
            || request.schemeType() == SchemeType.SLAB_DISCOUNT
                && (request.minimumQuantity() == null || request.discountPercentage() == null || request.buyQuantity() != null || request.freeQuantity() != null
                    || request.discountPercentage().signum() <= 0 || request.discountPercentage().compareTo(new java.math.BigDecimal("100")) > 0))
            throw new IllegalArgumentException("Invalid scheme fields.");
        if (request.startsAt() != null && request.endsAt() != null && !request.endsAt().isAfter(request.startsAt()))
            throw new IllegalArgumentException("End date must be after start date.");
        Retailer retailer = request.retailerId() == null ? null : retailers.findById(request.retailerId())
            .orElseThrow(() -> new ResourceNotFoundException("Retailer not found."));
        scheme.setProduct(product); scheme.setCategory(category);
        scheme.setDistributor(businesses.getReferenceById(distributorId)); scheme.setRetailer(retailer);
        scheme.setSchemeType(request.schemeType()); scheme.setName(request.name()); scheme.setDescription(request.description());
        scheme.setBuyQuantity(request.buyQuantity()); scheme.setFreeQuantity(request.freeQuantity());
        scheme.setMinimumQuantity(request.minimumQuantity()); scheme.setDiscountPercentage(request.discountPercentage());
        scheme.setStartsAt(request.startsAt()); scheme.setEndsAt(request.endsAt());
        if (scheme.getCreatedAt() == null) scheme.setCreatedAt(Instant.now());
        if (scheme.getUpdatedAt() == null) scheme.setUpdatedAt(Instant.now());
    }

    private UUID businessId(Authentication auth) {
        if (authorization == null || auth == null || !(auth.getPrincipal() instanceof UUID userId))
            throw new AccessDeniedException("Scheme management access denied.");
        java.util.Set<UUID> ids = authorization.activeOrderDeskBusinessIds(userId);
        if (ids.size() != 1) throw new AccessDeniedException("Select a single authorized distributor workspace.");
        return ids.iterator().next();
    }
    private String normalize(String q) { return q == null || q.isBlank() ? null : q.trim(); }

    private void requireManagement(Authentication auth, UUID distributorId) {
        if (authorization == null || auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UUID userId))
            throw new AccessDeniedException("Scheme management access denied.");
        authorization.requireOrderDeskAccess(userId, distributorId);
    }
}
