package com.vyapaaros.schemes.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.pricing.dto.ResolvedPrice;
import com.vyapaaros.schemes.dto.*;
import com.vyapaaros.schemes.entity.*;
import com.vyapaaros.schemes.repository.ProductSchemeRepository;
import java.math.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class SchemeResolutionService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final ProductSchemeRepository repository;
    private final ProductRepository products;
    private final DistributorAuthorizationService authorization;
    private final Clock clock;

    public SchemeResolutionService(ProductSchemeRepository repository, com.vyapaaros.retailer.repository.RetailerRepository retailers,
        com.vyapaaros.retailer.service.RetailerAuthorizationService oldAuthorization, Clock clock) {
        this(repository, (ProductRepository) null, (DistributorAuthorizationService) null, clock);
    }

    @Autowired
    public SchemeResolutionService(ProductSchemeRepository repository, ProductRepository products,
        DistributorAuthorizationService authorization, Clock clock) {
        this.repository = repository; this.products = products; this.authorization = authorization; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<UUID, SchemeSummary> summaries(Authentication auth, UUID retailerId, List<UUID> productIds) {
        return summaries(auth, retailerId, null, productIds);
    }

    @Transactional(readOnly = true)
    public Map<UUID, SchemeSummary> summaries(Authentication auth, UUID retailerId, UUID distributorId, List<UUID> productIds) {
        UUID businessId = authorize(auth, retailerId, distributorId, productIds);
        if (productIds.isEmpty()) return Map.of();
        Map<UUID, List<ProductScheme>> grouped = repository.findApplicable(productIds, retailerId, Instant.now(clock))
            .stream().collect(Collectors.groupingBy(s -> s.getProduct().getId()));
        Map<UUID, SchemeSummary> result = new HashMap<>();
        for (UUID id : productIds) {
            ProductScheme chosen = chooseSummary(grouped.getOrDefault(id, List.of()), retailerId);
            result.put(id, chosen == null ? SchemeSummary.unavailable() : SchemeSummary.from(chosen));
        }
        return result;
    }

    public SchemeEvaluation evaluate(UUID productId, UUID retailerId, int paidCases,
        ResolvedPrice price, Authentication auth) {
        if (paidCases < 0) throw new IllegalArgumentException("Paid cases cannot be negative.");
        authorize(auth, retailerId, null, List.of(productId));
        if (!price.priceAvailable()) return SchemeEvaluation.unavailable(paidCases);
        List<ProductScheme> schemes = repository.findApplicable(List.of(productId), retailerId, Instant.now(clock));
        BigDecimal base = price.pricePerCase().multiply(BigDecimal.valueOf(paidCases));
        ProductScheme selected = null; Calculation best = null;
        for (ProductScheme candidate : preferredByType(schemes, retailerId)) {
            Calculation calculation = calculate(candidate, paidCases, base, price.pricePerCase());
            if (calculation.qualifies() && (best == null || calculation.benefit().compareTo(best.benefit()) > 0
                || calculation.benefit().compareTo(best.benefit()) == 0
                && candidate.getSchemeType().ordinal() < selected.getSchemeType().ordinal())) {
                selected = candidate; best = calculation;
            }
        }
        if (selected == null) return new SchemeEvaluation(null, null, null, paidCases, 0, null,
            BigDecimal.ZERO.setScale(2), base.setScale(2), base.setScale(2),
            "No applicable scheme.", true);
        return new SchemeEvaluation(selected.getId(), selected.getName(), selected.getSchemeType(), paidCases,
            best.freeCases(), selected.getDiscountPercentage(), best.discountAmount(), base.setScale(2),
            best.finalAmount().setScale(2, RoundingMode.HALF_UP), best.explanation(), true);
    }

    private List<ProductScheme> preferredByType(List<ProductScheme> schemes, UUID retailerId) {
        Map<SchemeType, ProductScheme> selected = new EnumMap<>(SchemeType.class);
        for (ProductScheme scheme : schemes) {
            ProductScheme current = selected.get(scheme.getSchemeType());
            if (current == null || (retailerId != null && scheme.getRetailer() != null
                && current.getRetailer() == null) || (scheme.getId().toString().compareTo(current.getId().toString()) < 0
                && (scheme.getRetailer() == null) == (current.getRetailer() == null))) selected.put(scheme.getSchemeType(), scheme);
        }
        return new ArrayList<>(selected.values());
    }

    private ProductScheme chooseSummary(List<ProductScheme> schemes, UUID retailerId) {
        return preferredByType(schemes, retailerId).stream()
            .sorted(Comparator.comparing((ProductScheme s) -> s.getSchemeType().ordinal())
                .thenComparing(s -> s.getId().toString())).findFirst().orElse(null);
    }

    private Calculation calculate(ProductScheme scheme, int paid, BigDecimal base, BigDecimal unitPrice) {
        if (scheme.getSchemeType() == SchemeType.BUY_X_GET_Y_FREE) {
            int free = paid / scheme.getBuyQuantity() * scheme.getFreeQuantity();
            return new Calculation(free > 0, free, BigDecimal.ZERO.setScale(2), base,
                unitPrice.multiply(BigDecimal.valueOf(free)),
                "Buy " + scheme.getBuyQuantity() + " cases, get " + scheme.getFreeQuantity() + " free.");
        }
        if (paid < scheme.getMinimumQuantity()) return new Calculation(false, 0, BigDecimal.ZERO, base, BigDecimal.ZERO, "");
        BigDecimal discount = base.multiply(scheme.getDiscountPercentage()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new Calculation(true, 0, discount, base.subtract(discount), discount,
            scheme.getDiscountPercentage().stripTrailingZeros().toPlainString() + "% discount applied.");
    }

    private UUID authorize(Authentication auth, UUID retailerId, UUID distributorId, List<UUID> productIds) {
        if (authorization == null || products == null || auth == null || !auth.isAuthenticated()
            || !(auth.getPrincipal() instanceof UUID userId)) throw new AccessDeniedException("Scheme access denied.");
        UUID businessId;
        if (retailerId != null && has(auth, "ROLE_RETAILER")) {
            businessId = authorization.requireBusinessAccess(userId, retailerId, distributorId, false);
        } else if (retailerId == null && distributorId != null && has(auth, "ROLE_ORDER_DESK")) {
            businessId = authorization.requireBusinessAccess(userId, null, distributorId, true);
        } else throw new AccessDeniedException("Scheme access denied.");
        if (!productIds.isEmpty() && products.countByIdInAndDistributorId(productIds.stream().distinct().toList(), businessId)
            != productIds.stream().distinct().count()) throw new AccessDeniedException("Product is outside the authorized distributor.");
        return businessId;
    }
    private boolean has(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(role::equals);
    }
    private record Calculation(boolean qualifies, int freeCases, BigDecimal discountAmount,
        BigDecimal finalAmount, BigDecimal benefit, String explanation) {}
}
