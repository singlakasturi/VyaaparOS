package com.vyapaaros.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.notification.service.NotificationService;
import com.vyapaaros.order.dto.OrderActionRequestResponse;
import com.vyapaaros.order.entity.*;
import com.vyapaaros.order.repository.OrderActionRequestRepository;
import com.vyapaaros.order.repository.OrderRepository;
import com.vyapaaros.order.repository.OrderStatusHistoryRepository;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class OrderActionRequestServiceTest {
    private final OrderActionRequestRepository requests=mock(OrderActionRequestRepository.class);
    private final OrderRepository orders=mock(OrderRepository.class);
    private final OrderStatusHistoryRepository history=mock(OrderStatusHistoryRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final OrderScopeService retailerScope=mock(OrderScopeService.class);
    private final DistributorAuthorizationService distributorAuthorization=mock(DistributorAuthorizationService.class);
    private final InventoryRestorationService restoration=mock(InventoryRestorationService.class);
    private final NotificationService notifications=mock(NotificationService.class);
    private final OrderActionRequestService service=new OrderActionRequestService(requests,orders,history,users,retailerScope,
        distributorAuthorization,restoration,notifications);
    private final UUID actorId=UUID.randomUUID();
    private final UUID distributorId=UUID.randomUUID();
    private final UUID retailerId=UUID.randomUUID();
    private final UUID orderId=UUID.randomUUID();
    private final UUID requestId=UUID.randomUUID();
    private final User actor=new User();
    private Order order;

    @BeforeEach void setup() {
        actor.setId(actorId);
        order=new Order(); setId(order,"id",orderId); order.setOrderNumber("ORD-14"); order.setStatus(OrderStatus.APPROVED);
        order.setFulfillmentStatus(FulfillmentStatus.NOT_STARTED); order.setBaseAmount(BigDecimal.ZERO); order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(BigDecimal.ZERO); order.setCurrency("INR"); order.setCreatedAt(Instant.now()); order.setUpdatedAt(Instant.now());
        DistributorBusiness business=new DistributorBusiness(); business.setId(distributorId); order.setDistributor(business);
        Retailer retailer=new Retailer(); retailer.setId(retailerId); retailer.setBusinessName("Retail Shop"); order.setRetailer(retailer);
        when(retailerScope.userId(any())).thenReturn(actorId);
        doNothing().when(retailerScope).requireHistoricalOrderAccess(eq(actorId),eq(retailerId),any());
        when(users.findById(actorId)).thenReturn(Optional.of(actor));
        when(distributorAuthorization.activeOrderDeskBusinessIds(actorId)).thenReturn(Set.of(distributorId));
        when(requests.save(any())).thenAnswer(invocation->{OrderActionRequest saved=invocation.getArgument(0);if(saved.getId()==null)setId(saved,"id",requestId);return saved;});
    }

    @Test void cancellationEligibilityStopsAtShipmentAndExcludesExportedRejectedOrCancelledOrders() {
        for(OrderStatus status:List.of(OrderStatus.SUBMITTED,OrderStatus.CREDIT_HOLD,OrderStatus.APPROVED)) {
            order.setStatus(status);
            for(FulfillmentStatus fulfillment:List.of(FulfillmentStatus.NOT_STARTED,FulfillmentStatus.PREPARING)) {
                order.setFulfillmentStatus(fulfillment); assertTrue(OrderActionRequestService.cancellationEligible(order));
            }
        }
        order.setStatus(OrderStatus.TALLY_EXPORTED);order.setFulfillmentStatus(FulfillmentStatus.NOT_STARTED);assertFalse(OrderActionRequestService.cancellationEligible(order));
        order.setStatus(OrderStatus.REJECTED);assertFalse(OrderActionRequestService.cancellationEligible(order));
        order.setStatus(OrderStatus.CANCELLED);assertFalse(OrderActionRequestService.cancellationEligible(order));
        order.setStatus(OrderStatus.APPROVED);order.setFulfillmentStatus(FulfillmentStatus.SHIPPED);assertFalse(OrderActionRequestService.cancellationEligible(order));
        order.setFulfillmentStatus(FulfillmentStatus.DELIVERED);assertFalse(OrderActionRequestService.cancellationEligible(order));
    }

    @Test void returnEligibilityRequiresDeliveredAndNonCancelledOrder() {
        order.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
        assertTrue(OrderActionRequestService.returnEligible(order));
        order.setStatus(OrderStatus.TALLY_EXPORTED);assertTrue(OrderActionRequestService.returnEligible(order));
        order.setStatus(OrderStatus.CANCELLED);assertFalse(OrderActionRequestService.returnEligible(order));
        order.setStatus(OrderStatus.REJECTED);assertFalse(OrderActionRequestService.returnEligible(order));
        order.setStatus(OrderStatus.APPROVED);order.setFulfillmentStatus(FulfillmentStatus.SHIPPED);assertFalse(OrderActionRequestService.returnEligible(order));
    }

    @Test void retailerCanCreateOwnedCancellationRequestAndAuditNotificationAreWritten() {
        when(orders.findForUpdate(orderId)).thenReturn(Optional.of(order));
        when(requests.existsByOrderIdAndRequestTypeAndStatusIn(orderId,OrderActionRequestType.CANCELLATION,
            List.of(OrderActionRequestStatus.PENDING,OrderActionRequestStatus.APPROVED))).thenReturn(false);

        OrderActionRequestResponse response=service.requestCancellation(retailerAuth(),orderId,"Please stop this order");

        assertEquals(requestId,response.id());assertEquals(OrderActionRequestStatus.PENDING,response.status());
        verify(orders).findForUpdate(orderId);
        verify(retailerScope).requireHistoricalOrderAccess(eq(actorId),eq(retailerId),any());
        ArgumentCaptor<OrderStatusHistory> audit=ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history).save(audit.capture());assertEquals(OrderHistoryEventType.CANCELLATION_REQUEST,audit.getValue().getEventType());
        assertEquals(requestId,audit.getValue().getRequestId());assertEquals("PENDING",audit.getValue().getRequestStatus());
        verify(notifications).orderActionRequestReceived(requestId,distributorId,actorId,OrderActionRequestType.CANCELLATION);
    }

    @Test void returnCreationRequiresDeliveredOrderAndValidReason() {
        when(orders.findForUpdate(orderId)).thenReturn(Optional.of(order));
        order.setFulfillmentStatus(FulfillmentStatus.SHIPPED);
        assertThrows(ResourceConflictException.class,()->service.requestReturn(retailerAuth(),orderId,"Damaged"));
        order.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
        assertThrows(ResourceConflictException.class,()->service.requestReturn(retailerAuth(),orderId,"  "));
        assertThrows(ResourceConflictException.class,()->service.requestReturn(retailerAuth(),orderId,"x".repeat(501)));
        verify(requests,never()).save(any());
    }

    @Test void retailerMembershipBoundaryAndRepeatedRequestAreEnforced() {
        when(orders.findForUpdate(orderId)).thenReturn(Optional.of(order));
        doThrow(new AccessDeniedException("denied")).when(retailerScope).requireHistoricalOrderAccess(eq(actorId),eq(retailerId),any());
        assertThrows(AccessDeniedException.class,()->service.requestCancellation(retailerAuth(),orderId,null));
        verify(requests,never()).save(any());

        doNothing().when(retailerScope).requireHistoricalOrderAccess(eq(actorId),eq(retailerId),any());
        when(orders.findForUpdate(orderId)).thenReturn(Optional.of(order));
        when(requests.existsByOrderIdAndRequestTypeAndStatusIn(orderId,OrderActionRequestType.CANCELLATION,
            List.of(OrderActionRequestStatus.PENDING,OrderActionRequestStatus.APPROVED))).thenReturn(true);
        assertThrows(ResourceConflictException.class,()->service.requestCancellation(retailerAuth(),orderId,null));
        verify(requests,never()).save(any());
    }

    @Test void approvedCancellationRestoresReservedStockOnceAndCancelsOrder() {
        OrderActionRequest request=request(OrderActionRequestType.CANCELLATION,OrderActionRequestStatus.PENDING);
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(requests.findForUpdate(requestId)).thenReturn(Optional.of(request));
        when(orders.findForUpdate(orderId)).thenReturn(Optional.of(order));

        var result=service.approve(orderDeskAuth(),requestId,"Approved before shipment");

        assertEquals(OrderActionRequestStatus.APPROVED,result.status());assertEquals(OrderStatus.CANCELLED,order.getStatus());
        verify(restoration).restore(eq(order),any(com.vyapaaros.user.entity.User.class),eq("Cancellation request approved."));verify(orders).save(order);verify(orders).findForUpdate(orderId);
        verify(requests).findForUpdate(requestId);
        ArgumentCaptor<OrderStatusHistory> audit=ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history,times(2)).save(audit.capture());
        assertTrue(audit.getAllValues().stream().anyMatch(event->event.getEventType()==OrderHistoryEventType.CANCELLATION_DECISION));
        assertTrue(audit.getAllValues().stream().anyMatch(event->event.getEventType()==OrderHistoryEventType.ORDER_STATUS && event.getNewStatus()==OrderStatus.CANCELLED));
        verify(notifications).orderActionRequestDecision(requestId,actorId,OrderActionRequestType.CANCELLATION,true);
    }

    @Test void returnDecisionDoesNotRestoreInventoryOrChangeOrderStateAndRepeatIsRejected() {
        order.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
        OrderActionRequest request=request(OrderActionRequestType.RETURN,OrderActionRequestStatus.PENDING);
        request.setReason("Damaged");
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(requests.findForUpdate(requestId)).thenReturn(Optional.of(request));
        when(orders.findForUpdate(orderId)).thenReturn(Optional.of(order));

        var result=service.approve(orderDeskAuth(),requestId,null);

        assertEquals(OrderActionRequestStatus.APPROVED,result.status());assertEquals(FulfillmentStatus.DELIVERED,order.getFulfillmentStatus());
        assertEquals(OrderStatus.APPROVED,order.getStatus());verifyNoInteractions(restoration);verify(orders,never()).save(any());
        verify(notifications).orderActionRequestDecision(requestId,actorId,OrderActionRequestType.RETURN,true);

        request.setStatus(OrderActionRequestStatus.APPROVED);
        when(requests.findForUpdate(requestId)).thenReturn(Optional.of(request));
        assertThrows(ResourceConflictException.class,()->service.approve(orderDeskAuth(),requestId,null));
        verifyNoMoreInteractions(restoration);
    }

    @Test void cancellationApprovalFailsAfterOrderHasShippedAndDistributorScopeIsEnforced() {
        OrderActionRequest request=request(OrderActionRequestType.CANCELLATION,OrderActionRequestStatus.PENDING);
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(requests.findForUpdate(requestId)).thenReturn(Optional.of(request));
        when(orders.findForUpdate(orderId)).thenReturn(Optional.of(order));
        order.setFulfillmentStatus(FulfillmentStatus.SHIPPED);
        assertThrows(ResourceConflictException.class,()->service.approve(orderDeskAuth(),requestId,null));
        verify(restoration,never()).restore(any());

        order.setFulfillmentStatus(FulfillmentStatus.NOT_STARTED);
        when(distributorAuthorization.activeOrderDeskBusinessIds(actorId)).thenReturn(Set.of(UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class,()->service.approve(orderDeskAuth(),requestId,null));
        verify(requests,times(1)).findForUpdate(requestId);
    }

    private OrderActionRequest request(OrderActionRequestType type,OrderActionRequestStatus status) {
        OrderActionRequest request=new OrderActionRequest();setId(request,"id",requestId);request.setOrder(order);request.setRequestType(type);
        request.setStatus(status);request.setRequestedBy(actor);request.setCreatedAt(Instant.now());return request;
    }
    private UsernamePasswordAuthenticationToken retailerAuth() { return new UsernamePasswordAuthenticationToken(actorId,"n/a",List.of(new SimpleGrantedAuthority("ROLE_RETAILER"))); }
    private UsernamePasswordAuthenticationToken orderDeskAuth() { return new UsernamePasswordAuthenticationToken(actorId,"n/a",List.of(new SimpleGrantedAuthority("ROLE_ORDER_DESK"))); }
    private static void setId(Object entity,String fieldName,UUID id) {
        try {var field=entity.getClass().getDeclaredField(fieldName);field.setAccessible(true);field.set(entity,id);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
}
