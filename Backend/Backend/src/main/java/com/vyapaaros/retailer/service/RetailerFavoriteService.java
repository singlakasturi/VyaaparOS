package com.vyapaaros.retailer.service;

import com.vyapaaros.catalog.dto.ProductResponse;
import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.catalog.service.CatalogService;
import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.retailer.dto.RetailerFavoriteResponse;
import com.vyapaaros.retailer.repository.RetailerFavoriteRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetailerFavoriteService {
    private final RetailerAuthorizationService retailerAuthorization;
    private final DistributorAuthorizationService distributorAuthorization;
    private final RetailerFavoriteRepository favorites;
    private final ProductRepository products;
    private final CatalogService catalog;

    public RetailerFavoriteService(RetailerAuthorizationService retailerAuthorization,
        DistributorAuthorizationService distributorAuthorization, RetailerFavoriteRepository favorites,
        ProductRepository products, CatalogService catalog) {
        this.retailerAuthorization = retailerAuthorization;
        this.distributorAuthorization = distributorAuthorization;
        this.favorites = favorites;
        this.products = products;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<RetailerFavoriteResponse> list(Authentication authentication, UUID retailerId) {
        Context context = context(authentication, retailerId);
        return favorites.findByRetailerIdAndDistributorIdOrderBySavedAtDesc(retailerId, context.distributor().getId())
            .stream().map(favorite -> response(authentication, retailerId, favorite.getProduct(), favorite.getSavedAt()))
            .toList();
    }

    @Transactional
    public RetailerFavoriteResponse add(Authentication authentication, UUID retailerId, UUID productId) {
        Context context = context(authentication, retailerId);
        Product product = products.findByIdAndDistributorId(productId, context.distributor().getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found in the current distributor catalogue."));
        if (!product.isActive() || !product.getCategory().isActive()) {
            throw new ResourceConflictException("Only active products in the current catalogue can be saved as favorites.");
        }
        favorites.insertIfMissing(UUID.randomUUID(), retailerId, context.distributor().getId(), productId, Instant.now());
        var favorite = favorites.findByRetailerIdAndDistributorIdAndProductId(
            retailerId, context.distributor().getId(), productId)
            .orElseThrow(() -> new ResourceNotFoundException("Favorite could not be loaded."));
        return response(authentication, retailerId, product, favorite.getSavedAt());
    }

    @Transactional
    public void remove(Authentication authentication, UUID retailerId, UUID productId) {
        Context context = context(authentication, retailerId);
        favorites.deleteByRetailerIdAndDistributorIdAndProductId(retailerId, context.distributor().getId(), productId);
    }

    private RetailerFavoriteResponse response(Authentication authentication, UUID retailerId,
        Product product, Instant savedAt) {
        ProductResponse details = catalog.favoriteDetails(authentication, retailerId, product);
        boolean active = product.isActive() && product.getCategory().isActive();
        boolean orderable = active && details.availableCases() > 0 && details.priceAvailable();
        return new RetailerFavoriteResponse(product.getId(), savedAt, active, orderable, details);
    }

    private Context context(Authentication authentication, UUID retailerId) {
        UUID userId = userId(authentication);
        retailerAuthorization.requireMembership(userId, retailerId);
        DistributorBusiness distributor = distributorAuthorization.requireCurrentDistributorForRetailer(retailerId);
        return new Context(distributor);
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof UUID userId)
            || authentication.getAuthorities().stream().noneMatch(authority -> "ROLE_RETAILER".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Retailer favorites access denied.");
        }
        return userId;
    }

    private record Context(DistributorBusiness distributor) { }
}
