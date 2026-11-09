package com.vyapaaros.pricing.service;

import com.vyapaaros.catalog.entity.Product;
import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.pricing.dto.PriceRequest;
import com.vyapaaros.pricing.entity.ProductPrice;
import com.vyapaaros.pricing.repository.ProductPriceRepository;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.repository.RetailerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PriceManagementServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final RetailerRepository retailerRepository = mock(RetailerRepository.class);
    private final ProductPriceRepository priceRepository = mock(ProductPriceRepository.class);
    private final DistributorAuthorizationService authorization = mock(DistributorAuthorizationService.class);
    private PriceManagementService service;
    private final UUID productId = UUID.randomUUID();
    private final UUID retailerId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final DistributorBusiness distributor = new DistributorBusiness();

    @BeforeEach
    void setUp() {
        reset(productRepository, retailerRepository, priceRepository, authorization);
        distributor.setId(UUID.randomUUID());
        service = new PriceManagementService(
            productRepository,
            retailerRepository,
            priceRepository, authorization
        );
    }

    @Test
    void authorizedOrderDeskCreatesDefaultPrice() {
        Product product = new Product();
        product.setId(productId);
        product.setDistributor(distributor);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(priceRepository.findByProductIdAndDistributorIdAndRetailerIsNullAndActiveTrue(productId, distributor.getId()))
            .thenReturn(Optional.empty());
        when(priceRepository.save(any(ProductPrice.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ProductPrice price = service.upsertDefault(
            authentication("ROLE_ORDER_DESK", userId),
            productId,
            new PriceRequest(new BigDecimal("8.10"), "INR", null)
        );

        assertEquals(new BigDecimal("8.10"), price.getPricePerCase());
        assertNull(price.getGstIncluded());
        assertTrue(price.isActive());
        verify(authorization).requireOrderDeskAccess(userId, distributor.getId());
    }

    @Test
    void orderDeskCanDeactivateOverrideAndRetailerCannotManage() {
        ProductPrice existing = new ProductPrice();
        existing.setActive(true);
        existing.setDistributor(distributor);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product(productId)));
        when(priceRepository.findByProductIdAndDistributorIdAndRetailerIdAndActiveTrue(productId, distributor.getId(), retailerId))
            .thenReturn(Optional.of(existing));

        service.deactivateOverride(
            authentication("ROLE_ORDER_DESK", userId),
            retailerId,
            productId
        );
        assertFalse(existing.isActive());
        verify(priceRepository).save(existing);

        UUID retailerUser=UUID.randomUUID();
        doThrow(new AccessDeniedException("no business membership"))
            .when(authorization).requireOrderDeskAccess(retailerUser, distributor.getId());
        assertThrows(
            AccessDeniedException.class,
            () -> service.deactivateOverride(
                authentication("ROLE_RETAILER", retailerUser),
                retailerId,
                productId
            )
        );
    }

    @Test
    void rejectsMissingProductOrRetailer() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());
        assertThrows(
            ResourceNotFoundException.class,
            () -> service.upsertDefault(
                authentication("ROLE_ORDER_DESK"),
                productId,
                new PriceRequest(BigDecimal.ONE, "INR", Boolean.FALSE)
            )
        );

        when(retailerRepository.findById(retailerId)).thenReturn(Optional.empty());
        assertThrows(
            ResourceNotFoundException.class,
            () -> service.upsertOverride(
                authentication("ROLE_ORDER_DESK"),
                retailerId,
                productId,
                new PriceRequest(BigDecimal.ONE, "INR", Boolean.FALSE)
            )
        );
    }

    @Test
    void priceChangesArchivePreviousActivePrice() {
        Product product = product(productId);
        ProductPrice old = new ProductPrice();
        old.setPricePerCase(new BigDecimal("10.00")); old.setCurrency("INR"); old.setGstIncluded(false); old.setActive(true);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(priceRepository.findByProductIdAndDistributorIdAndRetailerIsNullAndActiveTrue(productId, distributor.getId()))
            .thenReturn(Optional.of(old));
        when(priceRepository.save(any(ProductPrice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductPrice updated = service.upsertDefault(authentication("ROLE_ORDER_DESK"), productId,
            new PriceRequest(new BigDecimal("12.50"), "INR", false));
        assertFalse(old.isActive()); assertNotSame(old, updated);
        assertEquals(new BigDecimal("12.50"), updated.getPricePerCase()); verify(priceRepository).save(old);
    }

    @Test
    void legacyGrantWithoutMembershipCannotManageDefaultPrice() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product(productId)));
        doThrow(new AccessDeniedException("no active membership"))
            .when(authorization).requireOrderDeskAccess(userId, distributor.getId());
        assertThrows(AccessDeniedException.class, () -> service.upsertDefault(authentication("ROLE_ADMIN"), productId,
            new PriceRequest(BigDecimal.ONE, "INR", false)));
        verify(priceRepository, never()).save(any());
    }

    private UsernamePasswordAuthenticationToken authentication(String role) { return authentication(role, userId); }
    private Product product(UUID id) { Product product = new Product(); product.setId(id); product.setDistributor(distributor); return product; }
    private UsernamePasswordAuthenticationToken authentication(String role, UUID user) {
        return new UsernamePasswordAuthenticationToken(
            user,
            null,
            List.of(new SimpleGrantedAuthority(role))
        );
    }
}
