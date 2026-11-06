package com.vyapaaros.schemes.service;

import com.vyapaaros.catalog.entity.Product;
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
    private final RetailerRepository retailers;
    private final DistributorAuthorizationService authorization;

    public SchemeManagementService(ProductSchemeRepository repository, ProductRepository products, RetailerRepository retailers) {
        this(repository, products, retailers, null);
    }

    @Autowired
    public SchemeManagementService(ProductSchemeRepository repository, ProductRepository products,
        RetailerRepository retailers, DistributorAuthorizationService authorization) {
        this.repository = repository; this.products = products; this.retailers = retailers; this.authorization = authorization;
    }

    @Transactional
    public ProductScheme create(Authentication auth, SchemeRequest request) {
        Product product = product(request.productId());
        requireManagement(auth, product.getDistributor().getId());
        authorizeRetailer(product.getDistributor().getId(), request.retailerId());
        ProductScheme scheme = new ProductScheme();
        apply(scheme, request, product);
        return repository.save(scheme);
    }

    @Transactional
    public ProductScheme update(Authentication auth, UUID id, SchemeRequest request) {
        ProductScheme scheme = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Scheme not found."));
        UUID businessId = scheme.getDistributor().getId();
        requireManagement(auth, businessId);
        Product product = product(request.productId());
        if (!businessId.equals(product.getDistributor().getId())) throw new AccessDeniedException("Scheme cannot be moved between distributors.");
        authorizeRetailer(businessId, request.retailerId());
        apply(scheme, request, product);
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
        return repository.searchByDistributor(distributorId, productId, retailerId, active);
    }

    private Product product(UUID productId) {
        return products.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Product not found."));
    }

    private void authorizeRetailer(UUID distributorId, UUID retailerId) {
        if (retailerId != null) authorization.requireActiveRetailerLink(retailerId, distributorId);
    }

    private void apply(ProductScheme scheme, SchemeRequest request, Product product) {
        if ((request.startsAt() != null && request.endsAt() != null && !request.endsAt().isAfter(request.startsAt()))
            || request.schemeType() == SchemeType.BUY_X_GET_Y_FREE
                && (request.buyQuantity() == null || request.freeQuantity() == null || request.minimumQuantity() != null || request.discountPercentage() != null)
            || request.schemeType() == SchemeType.SLAB_DISCOUNT
                && (request.minimumQuantity() == null || request.discountPercentage() == null || request.buyQuantity() != null || request.freeQuantity() != null
                    || request.discountPercentage().signum() <= 0 || request.discountPercentage().compareTo(new java.math.BigDecimal("100")) > 0))
            throw new IllegalArgumentException("Invalid scheme fields.");
        Retailer retailer = request.retailerId() == null ? null : retailers.findById(request.retailerId())
            .orElseThrow(() -> new ResourceNotFoundException("Retailer not found."));
        scheme.setProduct(product); scheme.setDistributor(product.getDistributor()); scheme.setRetailer(retailer);
        scheme.setSchemeType(request.schemeType()); scheme.setName(request.name()); scheme.setDescription(request.description());
        scheme.setBuyQuantity(request.buyQuantity()); scheme.setFreeQuantity(request.freeQuantity());
        scheme.setMinimumQuantity(request.minimumQuantity()); scheme.setDiscountPercentage(request.discountPercentage());
        scheme.setStartsAt(request.startsAt()); scheme.setEndsAt(request.endsAt()); scheme.setActive(true);
        if (scheme.getCreatedAt() == null) scheme.setCreatedAt(Instant.now());
        if (scheme.getUpdatedAt() == null) scheme.setUpdatedAt(Instant.now());
    }

    private void requireManagement(Authentication auth, UUID distributorId) {
        if (authorization == null || auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UUID userId))
            throw new AccessDeniedException("Scheme management access denied.");
        authorization.requireOrderDeskAccess(userId, distributorId);
    }
}
