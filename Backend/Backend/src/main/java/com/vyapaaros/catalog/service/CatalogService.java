package com.vyapaaros.catalog.service;

import com.vyapaaros.catalog.dto.CategoryResponse;
import com.vyapaaros.catalog.dto.ProductResponse;
import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.repository.ProductCategoryRepository;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.pricing.dto.ResolvedPrice;
import com.vyapaaros.pricing.service.PriceResolutionService;
import com.vyapaaros.schemes.dto.SchemeSummary;
import com.vyapaaros.schemes.service.SchemeResolutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CatalogService {
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final PriceResolutionService priceResolutionService;
    private final SchemeResolutionService schemeResolutionService;
    private final DistributorAuthorizationService authorization;

    public CatalogService(ProductCategoryRepository categories, ProductRepository products,
        InventoryRepository inventory, PriceResolutionService prices) {
        this(categories, products, inventory, prices, null, null);
    }

    public CatalogService(ProductCategoryRepository categories, ProductRepository products,
        InventoryRepository inventory, PriceResolutionService prices, SchemeResolutionService schemes) {
        this(categories, products, inventory, prices, schemes, null);
    }

    @Autowired
    public CatalogService(ProductCategoryRepository categories, ProductRepository products,
        InventoryRepository inventory, PriceResolutionService prices, SchemeResolutionService schemes,
        DistributorAuthorizationService authorization) {
        this.categoryRepository = categories;
        this.productRepository = products;
        this.inventoryRepository = inventory;
        this.priceResolutionService = prices;
        this.schemeResolutionService = schemes;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(Authentication auth, UUID retailerId, UUID distributorId) {
        UUID businessId = resolveBusiness(auth, retailerId, distributorId);
        return categoryRepository.findByDistributorIdAndActiveTrueOrderByNameAsc(businessId)
            .stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(Authentication auth) {
        return listCategories(auth, null, null);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(Authentication auth, String query, String categorySlug,
        Pageable pageable, UUID retailerId, UUID distributorId) {
        UUID businessId = resolveBusiness(auth, retailerId, distributorId);
        Page<Product> products = productRepository.searchActive(normalize(query), normalize(categorySlug), businessId, pageable);
        List<UUID> productIds = products.getContent().stream().map(Product::getId).toList();
        Map<UUID, ResolvedPrice> prices = priceResolutionService.resolve(auth, retailerId, businessId, productIds);
        Map<UUID, SchemeSummary> schemes = schemeResolutionService == null ? Map.of()
            : schemeResolutionService.summaries(auth, retailerId, businessId, productIds);
        Map<UUID, Integer> inventoryByProduct = productIds.isEmpty() ? Map.of()
            : inventoryRepository.findByProductIdInAndDistributorId(productIds, businessId).stream()
                .collect(Collectors.toMap(row -> row.getProduct().getId(), row -> row.getAvailableCases()));
        return products.map(product -> ProductResponse.from(product,
            inventoryByProduct.getOrDefault(product.getId(), 0),
            prices.getOrDefault(product.getId(), ResolvedPrice.unavailable()),
            schemes.getOrDefault(product.getId(), SchemeSummary.unavailable())));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(Authentication auth, String query, String categorySlug,
        Pageable pageable, UUID retailerId) {
        return listProducts(auth, query, categorySlug, pageable, retailerId, null);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Authentication auth, UUID productId, UUID retailerId, UUID distributorId) {
        UUID businessId = resolveBusiness(auth, retailerId, distributorId);
        Product product = productRepository.findByIdAndDistributorIdAndActiveTrue(productId, businessId)
            .filter(candidate -> candidate.getCategory().isActive())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        int availableCases = inventoryRepository.findByProductIdAndDistributorId(productId, businessId)
            .map(row -> row.getAvailableCases()).orElse(0);
        ResolvedPrice price = priceResolutionService.resolve(auth, retailerId, businessId, List.of(productId))
            .getOrDefault(productId, ResolvedPrice.unavailable());
        SchemeSummary scheme = schemeResolutionService == null ? SchemeSummary.unavailable()
            : schemeResolutionService.summaries(auth, retailerId, businessId, List.of(productId))
                .getOrDefault(productId, SchemeSummary.unavailable());
        return ProductResponse.from(product, availableCases, price, scheme);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Authentication auth, UUID productId, UUID retailerId) {
        return getProduct(auth, productId, retailerId, null);
    }

    private UUID resolveBusiness(Authentication auth, UUID retailerId, UUID distributorId) {
        if (authorization == null || auth == null || !auth.isAuthenticated()
            || !(auth.getPrincipal() instanceof UUID userId)) throw denied();
        boolean retailer = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_RETAILER"::equals);
        if (retailer && retailerId != null && distributorId == null)
            return authorization.requireBusinessAccess(userId, retailerId, null, false);
        if (retailerId == null && distributorId != null)
            return authorization.requireBusinessAccess(userId, null, distributorId, true);
        throw denied();
    }

    private AccessDeniedException denied() { return new AccessDeniedException("Distributor catalogue access denied."); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
