package com.vyapaaros.schemes.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.entity.ProductCategory;
import com.vyapaaros.catalog.repository.ProductCategoryRepository;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.retailer.repository.RetailerRepository;
import com.vyapaaros.schemes.dto.SchemeRequest;
import com.vyapaaros.schemes.entity.ProductScheme;
import com.vyapaaros.schemes.entity.SchemeType;
import com.vyapaaros.schemes.repository.ProductSchemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchemeManagementServiceTest {
    private final ProductSchemeRepository schemes = mock(ProductSchemeRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
    private final RetailerRepository retailers = mock(RetailerRepository.class);
    private final DistributorAuthorizationService authorization = mock(DistributorAuthorizationService.class);
    private final DistributorBusinessRepository businesses = mock(DistributorBusinessRepository.class);
    private final UUID userId = UUID.randomUUID(), distributorId = UUID.randomUUID(), productId = UUID.randomUUID(), categoryId = UUID.randomUUID();
    private final DistributorBusiness business = new DistributorBusiness();
    private final Product product = new Product();
    private final ProductCategory category = new ProductCategory();
    private SchemeManagementService service;

    @BeforeEach void setUp() {
        reset(schemes, products, categories, retailers, authorization, businesses);
        business.setId(distributorId); product.setId(productId); product.setDistributor(business); category.setId(categoryId); category.setDistributor(business); product.setCategory(category);
        when(authorization.activeOrderDeskBusinessIds(userId)).thenReturn(Set.of(distributorId));
        when(products.findByIdAndDistributorId(productId, distributorId)).thenReturn(Optional.of(product));
        when(categories.findByIdAndDistributorId(categoryId, distributorId)).thenReturn(Optional.of(category));
        when(businesses.getReferenceById(distributorId)).thenReturn(business);
        when(schemes.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new SchemeManagementService(schemes, products, categories, retailers, authorization, businesses);
    }

    @Test void ownerOrActiveDeskMembershipCanCreateProductScheme() {
        var saved = service.create(auth("ROLE_ORDER_DESK"), request(productId, null, null));
        assertEquals(distributorId, saved.getDistributor().getId()); assertEquals(productId, saved.getProduct().getId());
        verify(authorization).requireOrderDeskAccess(userId, distributorId);
    }

    @Test void globalLegacyGrantWithoutMembershipIsDenied() {
        doThrow(new AccessDeniedException("membership required")).when(authorization).requireOrderDeskAccess(userId, distributorId);
        assertThrows(AccessDeniedException.class, () -> service.create(auth("ROLE_ADMIN"), request(productId, null, null)));
        verify(schemes, never()).save(any());
    }

    @Test void categoryApplicabilityIsScopedToAuthorizedDistributor() {
        var saved = service.create(auth("ROLE_ORDER_DESK"), request(null, categoryId, null));
        assertEquals(categoryId, saved.getCategory().getId()); assertNull(saved.getProduct());
        when(categories.findByIdAndDistributorId(categoryId, distributorId)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.create(auth("ROLE_ORDER_DESK"), request(null, categoryId, null)));
    }

    @Test void acceptsDistributorWideSchemeAndRejectsInvalidDateOrBenefits() {
        assertNull(service.create(auth("ROLE_ORDER_DESK"), request(null, null, null)).getProduct());
        assertThrows(IllegalArgumentException.class, () -> service.create(auth("ROLE_ORDER_DESK"), request(productId, categoryId, null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(auth("ROLE_ORDER_DESK"), request(productId, null,
            new SchemeRequest(SchemeType.SLAB_DISCOUNT, "Bad date", null, productId, null, null,
                null, null, 3, new BigDecimal("5"), Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")))));
        assertThrows(IllegalArgumentException.class, () -> service.create(auth("ROLE_ORDER_DESK"),
            new SchemeRequest(SchemeType.SLAB_DISCOUNT, "Bad benefit", null, productId, null, null,
                null, null, 3, new BigDecimal("120"), null, null)));
    }

    @Test void deactivationPreservesSchemeRecord() {
        ProductScheme saved = new ProductScheme(); saved.setId(UUID.randomUUID()); saved.setDistributor(business); saved.setActive(true);
        when(schemes.findById(saved.getId())).thenReturn(Optional.of(saved));
        service.deactivate(auth("ROLE_ORDER_DESK"), saved.getId());
        assertFalse(saved.isActive()); verify(schemes).save(saved);
    }

    @Test void editingInactiveSchemeDoesNotReactivateIt() {
        ProductScheme saved = new ProductScheme(); saved.setId(UUID.randomUUID()); saved.setDistributor(business); saved.setActive(false);
        when(schemes.findById(saved.getId())).thenReturn(Optional.of(saved));
        ProductScheme updated = service.update(auth("ROLE_ORDER_DESK"), saved.getId(), request(productId, null, null));
        assertFalse(updated.isActive());
    }

    private SchemeRequest request(UUID product, UUID categoryId, SchemeRequest override) {
        return override != null ? override : new SchemeRequest(SchemeType.BUY_X_GET_Y_FREE, "Offer", "Description",
            product, categoryId, null, 10, 1, null, null, null, null);
    }
    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority(role)));
    }
}
