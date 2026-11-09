package com.vyapaaros.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.distributor.repository.DistributorMembershipRepository;
import com.vyapaaros.notification.entity.Notification;
import com.vyapaaros.notification.repository.NotificationRepository;
import com.vyapaaros.retailer.entity.RetailerMembershipRole;
import com.vyapaaros.retailer.entity.RetailerMembership;
import com.vyapaaros.retailer.repository.RetailerMembershipRepository;
import com.vyapaaros.order.entity.Order;
import com.vyapaaros.order.entity.FulfillmentStatus;
import com.vyapaaros.order.entity.OrderActionRequestType;
import com.vyapaaros.retailer.entity.Retailer;
import com.vyapaaros.user.entity.User;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
    private final NotificationRepository notifications=mock(NotificationRepository.class);
    private final DistributorMembershipRepository memberships=mock(DistributorMembershipRepository.class);
    private final DistributorBusinessRepository businesses=mock(DistributorBusinessRepository.class);
    private final RetailerMembershipRepository retailerMemberships=mock(RetailerMembershipRepository.class);
    private final NotificationService service=new NotificationService(notifications,memberships,businesses,retailerMemberships);
    private final UUID ownerId=UUID.randomUUID();

    @Test void listIsRecipientScopedAndPaged() {
        when(notifications.findByRecipientId(eq(ownerId),any())).thenReturn(org.springframework.data.domain.Page.empty());
        service.list(ownerId,1,500);
        var captor=org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(notifications).findByRecipientId(eq(ownerId),captor.capture());
        assertEquals(1,captor.getValue().getPageNumber());
        assertEquals(100,captor.getValue().getPageSize());
    }

    @Test void crossUserReadDoesNotRevealNotification() {
        UUID other=UUID.randomUUID();
        when(notifications.findByIdAndRecipientId(any(),eq(other))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,()->service.markRead(other,UUID.randomUUID()));
        verify(notifications,never()).save(any());
    }

    @Test void readAllOnlyUpdatesUnreadRecordsForCurrentRecipient() {
        service.markAllRead(ownerId);
        verify(notifications).markAllRead(eq(ownerId),any());
    }

    @Test void submittedOrderNotifiesPersistedRetailerOwnerWithDeduplicationKey() {
        UUID retailerId=UUID.randomUUID();
        User owner=new User(); owner.setId(ownerId);
        RetailerMembership membership=new RetailerMembership(); membership.setUser(owner);
        Retailer retailer=new Retailer(); retailer.setId(retailerId);
        Order order=new Order(); order.setId(UUID.randomUUID()); order.setRetailer(retailer);
        when(retailerMemberships.findFirstByRetailerIdAndRole(retailerId,RetailerMembershipRole.RETAILER_OWNER)).thenReturn(Optional.of(membership));

        service.orderSubmitted(order);

        verify(notifications).insertIfAbsent(any(),eq(ownerId),eq("ORDER_SUBMITTED"),eq("Order submitted"),any(),eq("ORDER"),eq(order.getId()),
            argThat(key->key.startsWith("ORDER_SUBMITTED:"+order.getId()+":"+ownerId)),any());
    }

    @Test void fulfillmentNotificationsAreLimitedToShipmentMilestones() {
        UUID retailerId=UUID.randomUUID(); UUID orderId=UUID.randomUUID();
        User owner=new User(); owner.setId(ownerId);
        RetailerMembership membership=new RetailerMembership(); membership.setUser(owner);
        Retailer retailer=new Retailer(); retailer.setId(retailerId);
        Order order=new Order(); order.setId(orderId); order.setRetailer(retailer);
        when(retailerMemberships.findFirstByRetailerIdAndRole(retailerId,RetailerMembershipRole.RETAILER_OWNER)).thenReturn(Optional.of(membership));

        order.setFulfillmentStatus(FulfillmentStatus.PREPARING);
        service.fulfillmentUpdate(order);
        verify(notifications,never()).insertIfAbsent(any(),any(),any(),any(),any(),any(),any(),any(),any());

        order.setFulfillmentStatus(FulfillmentStatus.SHIPPED);
        service.fulfillmentUpdate(order);
        verify(notifications).insertIfAbsent(any(),eq(ownerId),eq("ORDER_SHIPPED"),eq("Order shipped"),any(),eq("ORDER"),eq(orderId),
            eq("ORDER_SHIPPED:"+orderId+":"+ownerId),any());
    }

    @Test void cancellationAndReturnRequestNotificationsUseDistinctDedupedEvents() {
        UUID distributorId=UUID.randomUUID();UUID requestId=UUID.randomUUID();
        User businessOwner=new User();businessOwner.setId(ownerId);
        com.vyapaaros.distributor.entity.DistributorBusiness business=new com.vyapaaros.distributor.entity.DistributorBusiness();
        business.setId(distributorId);business.setCreatedBy(businessOwner);
        when(businesses.findById(distributorId)).thenReturn(Optional.of(business));

        service.orderActionRequestReceived(requestId,distributorId,UUID.randomUUID(),OrderActionRequestType.CANCELLATION);
        verify(notifications).insertIfAbsent(any(),eq(ownerId),eq("CANCELLATION_REQUEST_RECEIVED"),eq("Order cancellation requested"),
            any(),eq("ORDER_ACTION_REQUEST"),eq(requestId),eq("CANCELLATION_REQUEST_RECEIVED:"+requestId+":"+ownerId),any());
        service.orderActionRequestDecision(requestId,ownerId,OrderActionRequestType.RETURN,false);
        verify(notifications).insertIfAbsent(any(),eq(ownerId),eq("RETURN_REQUEST_REJECTED"),eq("Return request rejected"),
            any(),eq("ORDER_ACTION_REQUEST"),eq(requestId),eq("RETURN_REQUEST_REJECTED:"+requestId+":"+ownerId),any());
    }
}
