package com.vyapaaros.catalog.service;

import com.vyapaaros.catalog.entity.*;
import com.vyapaaros.catalog.dto.ProductResponse;
import com.vyapaaros.catalog.repository.*;
import com.vyapaaros.distributor.entity.*;
import com.vyapaaros.distributor.entity.RetailerDistributorRelationship.RelationshipStatus;
import com.vyapaaros.distributor.repository.*;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.inventory.entity.Inventory;
import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.pricing.dto.ResolvedPrice;
import com.vyapaaros.pricing.service.PriceResolutionService;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.retailer.service.RetailerAuthorizationService;
import com.vyapaaros.schemes.dto.SchemeSummary;
import com.vyapaaros.schemes.service.SchemeResolutionService;
import com.vyapaaros.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogServiceTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID retailerId = UUID.randomUUID();
    private final UUID distributorId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final PriceResolutionService prices = mock(PriceResolutionService.class);
    private final SchemeResolutionService schemes = mock(SchemeResolutionService.class);
    private final DistributorBusinessRepository businesses = mock(DistributorBusinessRepository.class);
    private final DistributorMembershipRepository memberships = mock(DistributorMembershipRepository.class);
    private final RetailerDistributorRelationshipRepository relationships = mock(RetailerDistributorRelationshipRepository.class);
    private final RetailerAuthorizationService retailerAuthorization = mock(RetailerAuthorizationService.class);
    private final DistributorAuthorizationService authorization = new DistributorAuthorizationService(
        businesses, memberships, relationships, retailerAuthorization);
    private CatalogService service;

    @BeforeEach void setUp() {
        reset(categories, products, inventory, prices, schemes, businesses, memberships, relationships, retailerAuthorization);
        User creator = new User(); creator.setId(UUID.randomUUID());
        DistributorBusiness business = new DistributorBusiness(); business.setId(distributorId); business.setCreatedBy(creator);
        when(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId, RelationshipStatus.ACTIVE))
            .thenReturn(Optional.of(relationship(business)));
        when(retailerAuthorization.requireMembership(userId, retailerId)).thenReturn(new com.vyapaaros.retailer.entity.RetailerMembership());
        service = new CatalogService(categories, products, inventory, prices, schemes, authorization);
    }

    @Test void listsOnlyCategoriesInCurrentBusiness() {
        ProductCategory category = category("biscuits", "Biscuits");
        when(categories.findByDistributorIdAndActiveTrueOrderByNameAsc(distributorId)).thenReturn(List.of(category));
        assertEquals(List.of("Biscuits"), service.listCategories(auth("ROLE_RETAILER"), retailerId, null)
            .stream().map(row -> row.name()).toList());
    }

    @Test void listsProductsWithScopedInventoryAndResolutions() {
        ProductCategory category = category("biscuits", "Biscuits"); Product product = product(category);
        Inventory stock = new Inventory(); stock.setProduct(product); stock.setAvailableCases(340);
        PageRequest pageable = PageRequest.of(1, 2);
        when(products.searchActive("glucose", "biscuits", distributorId, pageable))
            .thenReturn(new PageImpl<>(List.of(product), pageable, 3));
        when(prices.resolve(any(), eq(retailerId), eq(distributorId), eq(List.of(productId))))
            .thenReturn(Map.of(productId, ResolvedPrice.unavailable()));
        when(schemes.summaries(any(), eq(retailerId), eq(distributorId), eq(List.of(productId))))
            .thenReturn(Map.of(productId, SchemeSummary.unavailable()));
        when(inventory.findByProductIdInAndDistributorId(List.of(productId), distributorId)).thenReturn(List.of(stock));
        var result = service.listProducts(auth("ROLE_RETAILER"), " glucose ", "biscuits", pageable, retailerId, null);
        assertEquals(3, result.getTotalElements()); assertEquals(340, result.getContent().get(0).availableCases());
        verify(products).searchActive("glucose", "biscuits", distributorId, pageable);
    }

    @Test void rejectsUnscopedGlobalRolesAndMismatchedBusinessIds() {
        assertThrows(AccessDeniedException.class, () -> service.listCategories(auth("ROLE_ORDER_DESK"), null, null));
        assertThrows(AccessDeniedException.class, () -> service.listCategories(auth("ROLE_RETAILER"), retailerId, UUID.randomUUID()));
        verifyNoInteractions(categories);
    }

    @Test void rejectsDualRoleRetailerWhenDistributorIsClientSelected() {
        Authentication dualRole = new UsernamePasswordAuthenticationToken(userId, null, List.of(
            new SimpleGrantedAuthority("ROLE_RETAILER"), new SimpleGrantedAuthority("ROLE_ORDER_DESK")));
        assertThrows(AccessDeniedException.class,
            () -> service.listCategories(dualRole, null, distributorId));
        assertThrows(AccessDeniedException.class,
            () -> service.listProducts(dualRole, null, null, PageRequest.of(0, 20), retailerId, distributorId));
        verifyNoInteractions(categories, products);
    }

    @Test void rejectsNoAndEndedRelationshipsBeforeCatalogReads() {
        when(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId, RelationshipStatus.ACTIVE))
            .thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> service.listCategories(auth("ROLE_RETAILER"), retailerId, null));
        assertThrows(AccessDeniedException.class, () -> service.listProducts(auth("ROLE_RETAILER"), null, null,
            PageRequest.of(0, 20), retailerId, null));
        verifyNoInteractions(categories, products);
    }

    @Test void productSearchRespectsPageAndUnavailablePriceIsPreserved() {
        Product product = product(category("biscuits", "Biscuits"));
        PageRequest pageable = PageRequest.of(1, 5);
        when(products.searchActive("sku-75", null, distributorId, pageable))
            .thenReturn(new PageImpl<>(List.of(product), pageable, 13));
        when(prices.resolve(any(), eq(retailerId), eq(distributorId), eq(List.of(productId))))
            .thenReturn(Map.of(productId, ResolvedPrice.unavailable()));
        when(schemes.summaries(any(), eq(retailerId), eq(distributorId), eq(List.of(productId))))
            .thenReturn(Map.of(productId, SchemeSummary.unavailable()));
        when(inventory.findByProductIdInAndDistributorId(List.of(productId), distributorId)).thenReturn(List.of());
        var page = service.listProducts(auth("ROLE_RETAILER"), " sku-75 ", null, pageable, retailerId, null);
        assertEquals(13, page.getTotalElements()); assertEquals(1, page.getNumber());
        assertFalse(page.getContent().get(0).priceAvailable()); assertNull(page.getContent().get(0).pricePerCase());
        verify(products).searchActive("sku-75", null, distributorId, pageable);
    }

    @Test void productDetailResolvesCurrentPriceAndSelectedSchemeFromServer() {
        Product product = product(category("biscuits", "Biscuits"));
        when(products.findByIdAndDistributorIdAndActiveTrue(productId, distributorId)).thenReturn(Optional.of(product));
        when(inventory.findByProductIdAndDistributorId(productId, distributorId)).thenReturn(Optional.empty());
        ResolvedPrice resolved = new ResolvedPrice(true, new BigDecimal("120.00"), "INR", null, false);
        SchemeSummary scheme = new SchemeSummary(true, UUID.randomUUID(), com.vyapaaros.schemes.entity.SchemeType.SLAB_DISCOUNT,
            "5% offer", "Five percent from 3 cases", null, null, "PRODUCT", null, null, 3, new BigDecimal("5"));
        when(prices.resolve(any(), eq(retailerId), eq(distributorId), eq(List.of(productId)))).thenReturn(Map.of(productId, resolved));
        when(schemes.summaries(any(), eq(retailerId), eq(distributorId), eq(List.of(productId)))).thenReturn(Map.of(productId, scheme));
        ProductResponse response = service.getProduct(auth("ROLE_RETAILER"), productId, retailerId, null);
        assertTrue(response.priceAvailable()); assertEquals(new BigDecimal("120.00"), response.pricePerCase());
        assertEquals("5% offer", response.schemeName()); assertEquals("Five percent from 3 cases", response.schemeDescription());
    }

    private Authentication auth(String role) { return new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority(role))); }
    private RetailerDistributorRelationship relationship(DistributorBusiness business) {
        Retailer retailer = new Retailer(); retailer.setId(retailerId);
        RetailerDistributorRelationship relation = new RetailerDistributorRelationship(); relation.setRetailer(retailer);
        relation.setDistributor(business); relation.setStatus(RelationshipStatus.ACTIVE); return relation;
    }
    private ProductCategory category(String slug, String name) {
        ProductCategory c = new ProductCategory(); c.setId(UUID.randomUUID()); c.setSlug(slug); c.setName(name); c.setActive(true); return c;
    }
    private Product product(ProductCategory category) {
        Product p = new Product(); p.setId(productId); p.setDistributor(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId, RelationshipStatus.ACTIVE).orElseThrow().getDistributor());
        p.setCategory(category); p.setSku("SKU-BS-075"); p.setName("Glucose biscuit 75 g"); p.setUnitName("g");
        p.setUnitQuantity(BigDecimal.ONE); p.setUnitsPerCase(1); p.setActive(true); return p;
    }
}
