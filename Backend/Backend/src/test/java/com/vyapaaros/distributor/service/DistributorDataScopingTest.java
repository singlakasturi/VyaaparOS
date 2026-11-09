package com.vyapaaros.distributor.service;

import com.vyapaaros.catalog.entity.*;
import com.vyapaaros.catalog.repository.*;
import com.vyapaaros.catalog.service.CatalogService;
import com.vyapaaros.distributor.entity.*;
import com.vyapaaros.distributor.entity.RetailerDistributorRelationship.RelationshipStatus;
import com.vyapaaros.distributor.repository.*;
import com.vyapaaros.inventory.entity.Inventory;
import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.order.dto.OrderDeskExportRequest;
import com.vyapaaros.order.entity.*;
import com.vyapaaros.order.repository.*;
import com.vyapaaros.order.service.*;
import com.vyapaaros.pricing.dto.*;
import com.vyapaaros.pricing.entity.ProductPrice;
import com.vyapaaros.pricing.repository.ProductPriceRepository;
import com.vyapaaros.pricing.service.PriceResolutionService;
import com.vyapaaros.retailer.entity.*;
import com.vyapaaros.retailer.repository.*;
import com.vyapaaros.retailer.service.RetailerAuthorizationService;
import com.vyapaaros.schemes.repository.ProductSchemeRepository;
import com.vyapaaros.schemes.service.SchemeResolutionService;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DistributorDataScopingTest {
    private final UUID distroAId = UUID.randomUUID();
    private final UUID distroBId = UUID.randomUUID();
    private final UUID staffAId = UUID.randomUUID();
    private final UUID staffBId = UUID.randomUUID();
    private final UUID retailerAId = UUID.randomUUID();
    private final UUID retailerBId = UUID.randomUUID();
    private final UUID productAId = UUID.randomUUID();
    private final UUID productBId = UUID.randomUUID();
    private final UUID orderAId = UUID.randomUUID();
    private final UUID orderBId = UUID.randomUUID();
    private final UUID globalDeskOnlyId = UUID.randomUUID();

    private final DistributorBusinessRepository businesses = mock(DistributorBusinessRepository.class);
    private final DistributorMembershipRepository businessMemberships = mock(DistributorMembershipRepository.class);
    private final RetailerDistributorRelationshipRepository relationships = mock(RetailerDistributorRelationshipRepository.class);
    private final RetailerAuthorizationService retailerAuthorization = mock(RetailerAuthorizationService.class);
    private final DistributorAuthorizationService authorization = new DistributorAuthorizationService(
        businesses, businessMemberships, relationships, retailerAuthorization);
    private final RetailerMembershipRepository retailerMemberships = mock(RetailerMembershipRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final ProductPriceRepository prices = mock(ProductPriceRepository.class);
    private final ProductSchemeRepository schemes = mock(ProductSchemeRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderStatusHistoryRepository histories = mock(OrderStatusHistoryRepository.class);
    private final InventoryRestorationService restoration = mock(InventoryRestorationService.class);
    private final TallyExportBatchRepository batches = mock(TallyExportBatchRepository.class);
    private final TallyExportBatchOrderRepository batchOrders = mock(TallyExportBatchOrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Product productA = product(productAId, distroAId);
    private final Product productB = product(productBId, distroBId);
    private final Order orderA = order(orderAId, distroAId, OrderStatus.SUBMITTED);
    private final Order orderB = order(orderBId, distroBId, OrderStatus.SUBMITTED);
    private CatalogService catalog;
    private OrderDeskService orderDesk;

    @BeforeEach
    void setUp() {
        reset(businesses, businessMemberships, relationships, retailerAuthorization, products,
            categories, inventory, prices, schemes, orders, histories, restoration, batches,
            batchOrders, users);
        when(businesses.findById(distroAId)).thenReturn(Optional.of(business(distroAId, UUID.randomUUID())));
        when(businesses.findById(distroBId)).thenReturn(Optional.of(business(distroBId, UUID.randomUUID())));
        when(businessMemberships.existsByDistributorIdAndUserIdAndStatus(distroAId, staffAId, DistributorMembershipStatus.ACTIVE)).thenReturn(true);
        when(businessMemberships.existsByDistributorIdAndUserIdAndStatus(distroBId, staffBId, DistributorMembershipStatus.ACTIVE)).thenReturn(true);
        when(businessMemberships.findByUserIdAndStatus(staffAId, DistributorMembershipStatus.ACTIVE))
            .thenReturn(List.of(membership(staffAId, distroAId)));
        when(businessMemberships.findByUserIdAndStatus(staffBId, DistributorMembershipStatus.ACTIVE))
            .thenReturn(List.of(membership(staffBId, distroBId)));
        when(businessMemberships.findByUserIdAndStatus(globalDeskOnlyId, DistributorMembershipStatus.ACTIVE)).thenReturn(List.of());
        when(businesses.findAllByCreatedById(any(UUID.class))).thenReturn(List.of());
        when(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerAId, RelationshipStatus.ACTIVE))
            .thenReturn(Optional.of(relationship(retailerAId, distroAId)));
        when(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerBId, RelationshipStatus.ACTIVE))
            .thenReturn(Optional.of(relationship(retailerBId, distroBId)));
        when(relationships.existsByRetailerIdAndDistributorIdAndStatus(retailerAId, distroAId, RelationshipStatus.ACTIVE)).thenReturn(true);
        when(relationships.existsByRetailerIdAndDistributorIdAndStatus(retailerBId, distroBId, RelationshipStatus.ACTIVE)).thenReturn(true);

        when(products.searchActive(any(), any(), eq(distroAId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(productA)));
        when(products.searchActive(any(), any(), eq(distroBId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(productB)));
        when(products.countByIdInAndDistributorId(anyCollection(), any(UUID.class))).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            UUID requestedDistro = inv.getArgument(1);
            return ids.stream().filter(id -> (id.equals(productAId) && requestedDistro.equals(distroAId))
                || (id.equals(productBId) && requestedDistro.equals(distroBId))).count();
        });
        when(categories.findByDistributorIdAndActiveTrueOrderByNameAsc(distroAId)).thenReturn(List.of(productA.getCategory()));
        when(categories.findByDistributorIdAndActiveTrueOrderByNameAsc(distroBId)).thenReturn(List.of(productB.getCategory()));
        when(inventory.findByProductIdInAndDistributorId(List.of(productAId), distroAId))
            .thenReturn(List.of(inventoryFor(productA, 11)));
        when(inventory.findByProductIdInAndDistributorId(List.of(productBId), distroBId))
            .thenReturn(List.of(inventoryFor(productB, 22)));
        when(prices.findActiveDefaults(List.of(productAId), distroAId)).thenReturn(List.of(price(productA)));
        when(prices.findActiveDefaults(List.of(productBId), distroBId)).thenReturn(List.of(price(productB)));
        when(prices.findActiveOverrides(anyList(), any(UUID.class), any(UUID.class))).thenReturn(List.of());
        when(schemes.findApplicable(anyCollection(), any(), any(UUID.class), any(Instant.class))).thenReturn(List.of());

        PriceResolutionService priceResolution = new PriceResolutionService(prices, products, authorization);
        SchemeResolutionService schemeResolution = new SchemeResolutionService(schemes, products, authorization, Clock.systemUTC());
        catalog = new CatalogService(categories, products, inventory, priceResolution, schemeResolution, authorization);
        orderDesk = new OrderDeskService(orders, users, histories, restoration, batches, batchOrders, authorization);
    }

    @Test
    void distributorOrderDeskAndCatalogAreScopedToPersistedMembership() {
        var page = catalog.listProducts(auth(staffAId, "ROLE_ORDER_DESK"), null, null, PageRequest.of(0, 20), null, distroAId);
        assertEquals(List.of(productAId), page.getContent().stream().map(row -> row.id()).toList());
        assertEquals(11, page.getContent().get(0).availableCases());
        when(orders.searchByDistributorIds(Set.of(distroAId), null, null, OrderRepository.EARLIEST_ORDER_DATE,
            OrderRepository.LATEST_ORDER_DATE, null, PageRequest.of(0, 10)))
            .thenReturn(new PageImpl<>(List.of(orderA)));
        var result = orderDesk.list(auth(staffAId, "ROLE_ORDER_DESK"), null, null, null, null, null, PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
        verify(orders).searchByDistributorIds(Set.of(distroAId), null, null, OrderRepository.EARLIEST_ORDER_DATE,
            OrderRepository.LATEST_ORDER_DATE, null, PageRequest.of(0, 10));
    }

    @Test
    void staffCannotChangeRequestedBusinessOrOrderIdToReachAnotherDistributor() {
        assertThrows(AccessDeniedException.class, () -> catalog.listProducts(
            auth(staffAId, "ROLE_ORDER_DESK"), null, null, PageRequest.of(0, 20), null, distroBId));
        when(orders.findDetailed(orderBId)).thenReturn(Optional.of(orderB));
        assertThrows(AccessDeniedException.class, () -> orderDesk.get(auth(staffAId, "ROLE_ORDER_DESK"), orderBId));
        assertThrows(AccessDeniedException.class, () -> catalog.listProducts(
            auth(staffBId, "ROLE_ORDER_DESK"), null, null, PageRequest.of(0, 20), null, distroAId));
        when(orders.findDetailed(orderAId)).thenReturn(Optional.of(orderA));
        assertThrows(AccessDeniedException.class, () -> orderDesk.get(auth(staffBId, "ROLE_ORDER_DESK"), orderAId));
    }

    @Test
    void globalOrderDeskRoleWithoutBusinessMembershipCannotAccessAnyBusiness() {
        assertThrows(AccessDeniedException.class, () -> catalog.listProducts(
            auth(globalDeskOnlyId, "ROLE_ORDER_DESK"), null, null, PageRequest.of(0, 20), null, distroAId));
        assertThrows(AccessDeniedException.class, () -> orderDesk.list(
            auth(globalDeskOnlyId, "ROLE_ORDER_DESK"), null, null, null, null, null, PageRequest.of(0, 10)));
        verifyNoInteractions(orders);
    }

    @Test
    void retailerOnlySeesCurrentLinkedDistributorAndEndedLinkBlocksCatalog() {
        when(retailerAuthorization.requireMembership(staffAId, retailerAId)).thenReturn(new com.vyapaaros.retailer.entity.RetailerMembership());
        var result = catalog.listProducts(auth(staffAId, "ROLE_RETAILER"), null, null,
            PageRequest.of(0, 20), retailerAId, null);
        assertEquals(List.of(productAId), result.getContent().stream().map(row -> row.id()).toList());
        assertThrows(AccessDeniedException.class, () -> catalog.listProducts(auth(staffAId, "ROLE_RETAILER"),
            null, null, PageRequest.of(0, 20), retailerAId, distroBId));
        when(relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerAId, RelationshipStatus.ACTIVE))
            .thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, () -> catalog.listCategories(auth(staffAId, "ROLE_RETAILER"), retailerAId, null));
    }

    @Test
    void mixedDistributorOrdersCannotBeExportedInOneTallyBatch() {
        UUID tallyUser = staffAId;
        when(businessMemberships.findByUserIdAndStatus(tallyUser, DistributorMembershipStatus.ACTIVE))
            .thenReturn(List.of(membership(tallyUser, distroAId), membership(tallyUser, distroBId)));
        when(businessMemberships.existsByDistributorIdAndUserIdAndStatus(distroBId, tallyUser, DistributorMembershipStatus.ACTIVE)).thenReturn(true);
        User actor = new User(); actor.setId(tallyUser);
        when(users.findById(tallyUser)).thenReturn(Optional.of(actor));
        when(orders.findForUpdate(orderAId)).thenReturn(Optional.of(orderWithStatus(orderA, OrderStatus.APPROVED)));
        when(orders.findForUpdate(orderBId)).thenReturn(Optional.of(orderWithStatus(orderB, OrderStatus.APPROVED)));
        when(batchOrders.existsByOrderId(any(UUID.class))).thenReturn(false);
        var request = new OrderDeskExportRequest(List.of(orderAId, orderBId), null, null);
        assertThrows(com.vyapaaros.common.exception.ResourceConflictException.class,
            () -> orderDesk.export(auth(tallyUser, "ROLE_ORDER_DESK"), request));
        verifyNoInteractions(batches);
    }

    private Authentication auth(UUID id, String role) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority(role)));
    }
    private DistributorBusiness business(UUID id, UUID creator) {
        User owner = new User(); owner.setId(creator);
        DistributorBusiness business = new DistributorBusiness(); business.setId(id); business.setCreatedBy(owner); return business;
    }
    private DistributorMembership membership(UUID userId, UUID distributorId) {
        DistributorMembership membership = new DistributorMembership(); membership.setUser(user(userId));
        membership.setDistributor(business(distributorId, UUID.randomUUID())); membership.setStatus(DistributorMembershipStatus.ACTIVE); return membership;
    }
    private RetailerDistributorRelationship relationship(UUID retailerId, UUID distributorId) {
        RetailerDistributorRelationship relation = new RetailerDistributorRelationship();
        relation.setRetailer(retailer(retailerId)); relation.setDistributor(business(distributorId, UUID.randomUUID())); relation.setStatus(RelationshipStatus.ACTIVE); return relation;
    }
    private Product product(UUID id, UUID distributorId) {
        ProductCategory category = new ProductCategory(); category.setId(UUID.randomUUID()); category.setName("Category"); category.setSlug("category"); category.setActive(true);
        category.setDistributor(business(distributorId, UUID.randomUUID()));
        Product product = new Product(); product.setId(id); product.setDistributor(business(distributorId, UUID.randomUUID()));
        product.setCategory(category); product.setSku("SKU-" + id); product.setName("Product " + id); product.setUnitName("case");
        product.setUnitQuantity(BigDecimal.ONE); product.setUnitsPerCase(1); product.setActive(true); return product;
    }
    private ProductPrice price(Product product) {
        ProductPrice price = new ProductPrice(); price.setProduct(product); price.setDistributor(product.getDistributor());
        price.setPricePerCase(BigDecimal.TEN); price.setCurrency("INR"); price.setActive(true); return price;
    }
    private Inventory inventoryFor(Product product, int count) {
        Inventory row = new Inventory(); row.setProduct(product); row.setDistributor(product.getDistributor()); row.setAvailableCases(count); return row;
    }
    private Order order(UUID id, UUID distributorId, OrderStatus status) {
        Order order = new Order(); order.setId(id); order.setDistributor(business(distributorId, UUID.randomUUID()));
        order.setRetailer(retailer(distributorId.equals(distroAId) ? retailerAId : retailerBId));
        order.setStatus(status); order.setOrderNumber("ORD-" + id); order.setCurrency("INR");
        order.setBaseAmount(BigDecimal.TEN); order.setDiscountAmount(BigDecimal.ZERO); order.setFinalAmount(BigDecimal.TEN);
        order.setCreatedAt(Instant.now()); order.setUpdatedAt(Instant.now()); return order;
    }
    private Order orderWithStatus(Order order, OrderStatus status) { order.setStatus(status); return order; }
    private Retailer retailer(UUID id) { Retailer retailer = new Retailer(); retailer.setId(id); retailer.setBusinessName("Retailer"); return retailer; }
    private User user(UUID id) { User user = new User(); user.setId(id); return user; }
}
