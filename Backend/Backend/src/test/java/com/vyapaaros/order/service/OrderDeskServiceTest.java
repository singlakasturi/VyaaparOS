package com.vyapaaros.order.service;

import com.vyapaaros.catalog.entity.Product;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.order.entity.*;
import com.vyapaaros.order.dto.OrderStatusCount;
import com.vyapaaros.order.dto.FulfillmentUpdateRequest;
import com.vyapaaros.order.repository.*;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.distributor.entity.*;
import com.vyapaaros.distributor.repository.*;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.retailer.service.RetailerAuthorizationService;
import com.vyapaaros.notification.service.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class OrderDeskServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    private final InventoryRestorationService restoration = mock(InventoryRestorationService.class);
    private final TallyExportBatchRepository batches = mock(TallyExportBatchRepository.class);
    private final TallyExportBatchOrderRepository batchOrders = mock(TallyExportBatchOrderRepository.class);
    private final DistributorBusinessRepository businesses = mock(DistributorBusinessRepository.class);
    private final DistributorMembershipRepository memberships = mock(DistributorMembershipRepository.class);
    private final RetailerDistributorRelationshipRepository relationships = mock(RetailerDistributorRelationshipRepository.class);
    private final RetailerAuthorizationService retailerAuthorization = mock(RetailerAuthorizationService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final DistributorAuthorizationService authorization = new DistributorAuthorizationService(businesses,memberships,relationships,retailerAuthorization);
    private final UUID actorId = UUID.randomUUID();
    private final User actor = new User();
    private final UUID distributorId = UUID.randomUUID();
    private final DistributorBusiness distributor = new DistributorBusiness();
    private OrderDeskService service;

    @BeforeEach
    void setUp() {
        actor.setId(actorId);
        when(users.findById(actorId)).thenReturn(Optional.of(actor));
        distributor.setId(distributorId); distributor.setCreatedBy(actor);
        when(businesses.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(businesses.findAllByCreatedById(actorId)).thenReturn(List.of());
        when(memberships.findByUserIdAndStatus(actorId,DistributorMembershipStatus.ACTIVE)).thenReturn(List.of(membership()));
        when(memberships.existsByDistributorIdAndUserIdAndStatus(distributorId,actorId,DistributorMembershipStatus.ACTIVE)).thenReturn(true);
        service = new OrderDeskService(orders, users, history, restoration, batches, batchOrders, authorization, notifications);
    }

    @Test
    void retailerCannotUseOrderDesk() {
        var auth = new UsernamePasswordAuthenticationToken(UUID.randomUUID(), "n/a", List.of(new SimpleGrantedAuthority("ROLE_RETAILER")));
        assertThrows(AccessDeniedException.class, () -> service.get(auth, UUID.randomUUID()));
        verifyNoInteractions(orders);
    }

    @Test
    void submittedOrderCanBeApprovedWithoutOverrideReason() {
        Order order = order(OrderStatus.SUBMITTED);
        when(orders.findForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.approve(auth("ROLE_ORDER_DESK"), order.getId(), null);

        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.APPROVED, order.getStatus());
        ArgumentCaptor<OrderStatusHistory> entry = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history).save(entry.capture());
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.SUBMITTED, entry.getValue().getPreviousStatus());
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.APPROVED, entry.getValue().getNewStatus());
        org.junit.jupiter.api.Assertions.assertEquals(actor, entry.getValue().getChangedBy());
        org.junit.jupiter.api.Assertions.assertNotNull(entry.getValue().getCreatedAt());
        verify(restoration, never()).restore(any());
    }

    @Test
    void overviewCountsAndRecentOrdersAreScopedToResolvedDistributor() {
        Order recent = order(OrderStatus.SUBMITTED);
        when(orders.countByStatusForDistributors(Set.of(distributorId)))
            .thenReturn(List.of(new OrderStatusCount(OrderStatus.SUBMITTED, 2)));
        when(orders.findRecentByDistributorIds(eq(Set.of(distributorId)), any(PageRequest.class)))
            .thenReturn(List.of(recent));

        var result = service.overview(auth("ROLE_ORDER_DESK"));

        org.junit.jupiter.api.Assertions.assertEquals(2L, result.statusCounts().get(OrderStatus.SUBMITTED));
        org.junit.jupiter.api.Assertions.assertEquals(0L, result.statusCounts().get(OrderStatus.REJECTED));
        org.junit.jupiter.api.Assertions.assertEquals(2L, result.totalOrders());
        org.junit.jupiter.api.Assertions.assertEquals(recent.getId(), result.recentOrders().getFirst().id());
        verify(orders).countByStatusForDistributors(Set.of(distributorId));
        verify(orders).findRecentByDistributorIds(eq(Set.of(distributorId)), any(PageRequest.class));
    }

    @Test
    void queueSearchIsTrimmedAndDistributorScopedWithPagination() {
        var auth = auth("ROLE_ORDER_DESK");
        var pageable = PageRequest.of(2, 10);
        when(orders.searchByDistributorIds(Set.of(distributorId), OrderStatus.SUBMITTED, null,
            OrderRepository.EARLIEST_ORDER_DATE, OrderRepository.LATEST_ORDER_DATE, "ord-1", pageable))
            .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.list(auth, OrderStatus.SUBMITTED, null, null, null, "  ORD-1  ", pageable);

        verify(orders).searchByDistributorIds(Set.of(distributorId), OrderStatus.SUBMITTED, null,
            OrderRepository.EARLIEST_ORDER_DATE, OrderRepository.LATEST_ORDER_DATE, "ord-1", pageable);
    }

    @Test
    void crossDistributorOrderCannotBeReadByAuthorizedStaff() {
        UUID otherDistributorId = UUID.randomUUID();
        Order order = order(OrderStatus.SUBMITTED);
        DistributorBusiness other = new DistributorBusiness(); other.setId(otherDistributorId);
        order.setDistributor(other);
        when(orders.findDetailed(order.getId())).thenReturn(Optional.of(order));
        when(businesses.findById(otherDistributorId)).thenReturn(Optional.empty());
        when(memberships.existsByDistributorIdAndUserIdAndStatus(otherDistributorId, actorId,
            DistributorMembershipStatus.ACTIVE)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.get(auth("ROLE_ORDER_DESK"), order.getId()));
    }

    @Test
    void repeatedRejectionCannotRestoreStockOrAppendHistoryTwice() {
        Order order = order(OrderStatus.SUBMITTED);
        OrderItem snapshot = new OrderItem();
        Product product = new Product(); product.setId(UUID.randomUUID());
        snapshot.setProduct(product); snapshot.setProductSku("SKU-HIST"); snapshot.setProductName("Historical item");
        snapshot.setPaidCases(3); snapshot.setFreeCases(1); snapshot.setTotalCases(4);
        snapshot.setUnitPricePerCase(new BigDecimal("12.50")); snapshot.setCurrency("INR");
        snapshot.setBaseAmount(new BigDecimal("37.50")); snapshot.setDiscountAmount(new BigDecimal("2.50"));
        snapshot.setFinalAmount(new BigDecimal("35.00")); snapshot.setSchemeName("Saved scheme");
        order.addItem(snapshot);
        when(orders.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        var auth = auth("ROLE_ORDER_DESK");

        service.reject(auth, order.getId(), "Duplicate order");
        assertThrows(ResourceConflictException.class, () -> service.reject(auth, order.getId(), "Retry"));

        verify(restoration, times(1)).restore(eq(order),eq(actor),eq("Duplicate order"));
        verify(history, times(1)).save(any(OrderStatusHistory.class));
        org.junit.jupiter.api.Assertions.assertEquals("SKU-HIST", order.getItems().getFirst().getProductSku());
        org.junit.jupiter.api.Assertions.assertEquals("Historical item", order.getItems().getFirst().getProductName());
        org.junit.jupiter.api.Assertions.assertEquals(4, order.getItems().getFirst().getTotalCases());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("35.00"), order.getItems().getFirst().getFinalAmount());
        org.junit.jupiter.api.Assertions.assertEquals("Saved scheme", order.getItems().getFirst().getSchemeName());
    }

    @Test
    void rejectedOrderCannotBeApproved() {
        Order order = order(OrderStatus.REJECTED);
        when(orders.findForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThrows(ResourceConflictException.class,
            () -> service.approve(auth("ROLE_ORDER_DESK"), order.getId(), "try again"));

        verify(history, never()).save(any());
        verify(restoration, never()).restore(any());
    }

    @Test
    void creditHoldRequiresReasonAndRejectionRestoresOnce() {
        Order order = order(OrderStatus.CREDIT_HOLD);
        when(orders.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        var auth = auth("ROLE_ADMIN");

        assertThrows(ResourceConflictException.class, () -> service.approve(auth, order.getId(), " "));
        service.reject(auth, order.getId(), "Customer review failed");

        verify(restoration).restore(eq(order),eq(actor),eq("Customer review failed"));
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.REJECTED, order.getStatus());
        verify(history).save(any(OrderStatusHistory.class));
    }

    @Test
    void fulfillmentTransitionsAreForwardOnlyAndAudited() {
        Order order=order(OrderStatus.APPROVED);
        when(orders.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        var auth=auth("ROLE_ORDER_DESK");

        var preparing=service.updateFulfillment(auth,order.getId(),new FulfillmentUpdateRequest(FulfillmentStatus.PREPARING,null));
        assertEquals(FulfillmentStatus.PREPARING,preparing.fulfillmentStatus());
        assertNotNull(preparing.fulfillmentStartedAt());
        var shipped=service.updateFulfillment(auth,order.getId(),new FulfillmentUpdateRequest(FulfillmentStatus.SHIPPED,"  TRK-42 / A  "));
        assertEquals("TRK-42 / A",shipped.trackingReference());
        assertNotNull(shipped.shippedAt());
        var delivered=service.updateFulfillment(auth,order.getId(),new FulfillmentUpdateRequest(FulfillmentStatus.DELIVERED,"TRK-42 / A"));
        assertEquals(FulfillmentStatus.DELIVERED,delivered.fulfillmentStatus());
        verify(restoration).consume(order,actor);
        assertNotNull(delivered.deliveredAt());

        ArgumentCaptor<OrderStatusHistory> events=ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history,times(3)).save(events.capture());
        assertTrue(events.getAllValues().stream().allMatch(event -> event.getEventType()==OrderHistoryEventType.FULFILLMENT));
        assertEquals(FulfillmentStatus.NOT_STARTED,events.getAllValues().getFirst().getPreviousFulfillmentStatus());
        assertEquals(FulfillmentStatus.DELIVERED,events.getAllValues().getLast().getNewFulfillmentStatus());
        verify(notifications,times(3)).fulfillmentUpdate(order);
        assertEquals(OrderStatus.APPROVED,order.getStatus());
    }

    @Test
    void fulfillmentRejectsInvalidJumpsRejectedOrdersAndBadTrackingReferences() {
        var auth=auth("ROLE_ORDER_DESK");
        Order approved=order(OrderStatus.APPROVED);
        when(orders.findForUpdate(approved.getId())).thenReturn(Optional.of(approved));
        assertThrows(ResourceConflictException.class,()->service.updateFulfillment(auth,approved.getId(),
            new FulfillmentUpdateRequest(FulfillmentStatus.SHIPPED,null)));
        assertThrows(ResourceConflictException.class,()->service.updateFulfillment(auth,approved.getId(),
            new FulfillmentUpdateRequest(FulfillmentStatus.PREPARING,"bad\nreference")));
        verify(history,never()).save(any());

        Order rejected=order(OrderStatus.REJECTED);
        when(orders.findForUpdate(rejected.getId())).thenReturn(Optional.of(rejected));
        assertThrows(ResourceConflictException.class,()->service.updateFulfillment(auth,rejected.getId(),
            new FulfillmentUpdateRequest(FulfillmentStatus.PREPARING,null)));
    }

    @Test
    void fulfillmentUpdateRequiresActiveDistributorAccessAndUsesLockedOrderRead() {
        UUID otherId=UUID.randomUUID();
        Order order=order(OrderStatus.TALLY_EXPORTED);
        DistributorBusiness other=new DistributorBusiness(); other.setId(otherId);
        order.setDistributor(other);
        when(orders.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(businesses.findById(otherId)).thenReturn(Optional.empty());
        when(memberships.existsByDistributorIdAndUserIdAndStatus(otherId,actorId,DistributorMembershipStatus.ACTIVE)).thenReturn(false);

        assertThrows(AccessDeniedException.class,()->service.updateFulfillment(auth("ROLE_ORDER_DESK"),order.getId(),
            new FulfillmentUpdateRequest(FulfillmentStatus.PREPARING,null)));
        verify(orders).findForUpdate(order.getId());
        verify(history,never()).save(any());
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setStatus(status);
        order.setDistributor(distributor);
        Retailer retailer = mock(Retailer.class);
        when(retailer.getId()).thenReturn(UUID.randomUUID());
        when(retailer.getBusinessName()).thenReturn("Test Retailer");
        when(retailer.getContactPhone()).thenReturn("000");
        order.setRetailer(retailer);
        order.setOrderNumber("ORD-1");
        order.setBaseAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(BigDecimal.ZERO);
        order.setCurrency("INR");
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        try {
            var field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return order;
    }

    private DistributorMembership membership() { DistributorMembership m=new DistributorMembership(); m.setUser(actor); m.setDistributor(distributor); m.setStatus(DistributorMembershipStatus.ACTIVE); return m; }

    private UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(actorId, "n/a", List.of(new SimpleGrantedAuthority(role)));
    }
}
