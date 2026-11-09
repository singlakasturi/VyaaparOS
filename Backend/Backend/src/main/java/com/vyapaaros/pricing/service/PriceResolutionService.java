package com.vyapaaros.pricing.service;

import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.pricing.dto.PriceSource;
import com.vyapaaros.pricing.dto.ResolvedPrice;
import com.vyapaaros.pricing.entity.ProductPrice;
import com.vyapaaros.pricing.repository.ProductPriceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PriceResolutionService {
    private final ProductPriceRepository priceRepository;
    private final ProductRepository products;
    private final DistributorAuthorizationService authorization;

    public PriceResolutionService(ProductPriceRepository prices) { this(prices, null, null); }
    @Autowired
    public PriceResolutionService(ProductPriceRepository prices, ProductRepository products,
        DistributorAuthorizationService authorization) {
        this.priceRepository = prices; this.products = products; this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Map<UUID, ResolvedPrice> resolve(Authentication auth, UUID retailerId, List<UUID> productIds) {
        return resolve(auth, retailerId, null, productIds);
    }

    @Transactional(readOnly = true)
    public Map<UUID, ResolvedPrice> resolve(Authentication auth, UUID retailerId, UUID requestedDistributorId,
        List<UUID> productIds) {
        UUID distributorId = resolveBusiness(auth, retailerId, requestedDistributorId);
        if (productIds.isEmpty()) return Map.of();
        if (products == null || products.countByIdInAndDistributorId(productIds.stream().distinct().toList(), distributorId)
            != productIds.stream().distinct().count()) throw denied();

        Map<UUID, ProductPrice> defaults = priceRepository.findActiveDefaults(productIds, distributorId).stream()
            .collect(Collectors.toMap(price -> price.getProduct().getId(), Function.identity(), (first, ignored) -> first));
        Map<UUID, ProductPrice> overrides = retailerId == null ? Map.of()
            : priceRepository.findActiveOverrides(productIds, retailerId, distributorId).stream()
                .collect(Collectors.toMap(price -> price.getProduct().getId(), Function.identity(), (first, ignored) -> first));
        Map<UUID, ResolvedPrice> resolved = new HashMap<>();
        for (UUID productId : productIds) {
            ProductPrice override = overrides.get(productId);
            ProductPrice fallback = defaults.get(productId);
            resolved.put(productId, override != null ? ResolvedPrice.from(override, PriceSource.RETAILER_OVERRIDE)
                : fallback == null ? ResolvedPrice.unavailable() : ResolvedPrice.from(fallback, PriceSource.DEFAULT_DEALER_PRICE));
        }
        return resolved;
    }

    private UUID resolveBusiness(Authentication auth, UUID retailerId, UUID requestedDistributorId) {
        if (authorization == null || auth == null || !(auth.getPrincipal() instanceof UUID userId)) throw denied();
        boolean isRetailer = has(auth, "ROLE_RETAILER");
        if (retailerId != null && isRetailer) {
            return authorization.requireBusinessAccess(userId, retailerId, requestedDistributorId, false);
        }
        if (retailerId == null && requestedDistributorId != null) {
            return authorization.requireBusinessAccess(userId, null, requestedDistributorId, true);
        }
        throw denied();
    }

    private boolean has(Authentication auth, String role) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(role::equals);
    }
    private AccessDeniedException denied() { return new AccessDeniedException("Distributor price access denied."); }
}
