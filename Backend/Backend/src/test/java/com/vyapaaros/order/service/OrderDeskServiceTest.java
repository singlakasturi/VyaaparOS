package com.vyapaaros.order.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.order.entity.*;
import com.vyapaaros.order.repository.*;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.distributor.entity.*;
import com.vyapaaros.distributor.repository.*;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.retailer.service.RetailerAuthorizationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
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
        service = new OrderDeskService(orders, users, history, restoration, batches, batchOrders, authorization);
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
        verify(history).save(any(OrderStatusHistory.class));
        verify(restoration, never()).restore(any());
    }

    @Test
    void creditHoldRequiresReasonAndRejectionRestoresOnce() {
        Order order = order(OrderStatus.CREDIT_HOLD);
        when(orders.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        var auth = auth("ROLE_ADMIN");

        assertThrows(ResourceConflictException.class, () -> service.approve(auth, order.getId(), " "));
        service.reject(auth, order.getId(), "Customer review failed");

        verify(restoration).restore(order);
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.REJECTED, order.getStatus());
        verify(history).save(any(OrderStatusHistory.class));
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
