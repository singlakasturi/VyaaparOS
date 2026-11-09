package com.vyapaaros.notification.service;

import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.entity.DistributorMembershipStatus;
import com.vyapaaros.distributor.repository.DistributorMembershipRepository;
import com.vyapaaros.distributor.repository.DistributorBusinessRepository;
import com.vyapaaros.retailer.entity.RetailerMembershipRole;
import com.vyapaaros.retailer.repository.RetailerMembershipRepository;
import com.vyapaaros.notification.dto.NotificationResponse;
import com.vyapaaros.notification.entity.Notification;
import com.vyapaaros.notification.entity.NotificationType;
import com.vyapaaros.notification.repository.NotificationRepository;
import com.vyapaaros.order.entity.Order;
import com.vyapaaros.order.entity.FulfillmentStatus;
import com.vyapaaros.order.entity.OrderActionRequestType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final DistributorMembershipRepository memberships;
    private final DistributorBusinessRepository businesses;
    private final RetailerMembershipRepository retailerMemberships;
    public NotificationService(NotificationRepository notifications, DistributorMembershipRepository memberships, DistributorBusinessRepository businesses, RetailerMembershipRepository retailerMemberships) {
        this.notifications=notifications; this.memberships=memberships; this.businesses=businesses; this.retailerMemberships=retailerMemberships;
    }
    @Transactional(readOnly=true)
    public Page<NotificationResponse> list(UUID userId, int page, int size) {
        return notifications.findByRecipientId(userId, PageRequest.of(Math.max(0,page), Math.min(Math.max(1,size),100),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))).map(NotificationResponse::from);
    }
    @Transactional(readOnly=true) public long unreadCount(UUID userId) { return notifications.countByRecipientIdAndReadAtIsNull(userId); }
    @Transactional public void markRead(UUID userId, UUID id) {
        Notification n=notifications.findByIdAndRecipientId(id,userId).orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        if(n.getReadAt()==null) n.setReadAt(Instant.now());
    }
    @Transactional public void markAllRead(UUID userId) { notifications.markAllRead(userId,Instant.now()); }

    @Transactional public void orderSubmitted(Order order) {
        orderRetailerOwner(order).ifPresent(recipient -> create(recipient, NotificationType.ORDER_SUBMITTED, "Order submitted", "Your order was submitted to the distributor.", "ORDER", order.getId()));
    }
    @Transactional public void orderDecision(Order order, boolean approved) {
        NotificationType type=approved?NotificationType.ORDER_APPROVED:NotificationType.ORDER_REJECTED;
        orderRetailerOwner(order).ifPresent(recipient -> create(recipient,type,approved?"Order approved":"Order rejected",approved?"The distributor approved your order.":"The distributor rejected your order.","ORDER",order.getId()));
    }
    @Transactional public void fulfillmentUpdate(Order order) {
        if (order.getFulfillmentStatus() != FulfillmentStatus.SHIPPED && order.getFulfillmentStatus() != FulfillmentStatus.DELIVERED) return;
        boolean delivered=order.getFulfillmentStatus()==FulfillmentStatus.DELIVERED;
        NotificationType type=delivered?NotificationType.ORDER_DELIVERED:NotificationType.ORDER_SHIPPED;
        orderRetailerOwner(order).ifPresent(recipient -> create(recipient,type,delivered?"Order delivered":"Order shipped",
            delivered?"Your order has been marked delivered.":"Your order has been shipped.","ORDER",order.getId()));
    }
    @Transactional public void relationshipRequestReceived(UUID requestId, UUID distributorId, UUID actorId) {
        Set<UUID> recipients=new LinkedHashSet<>();
        notificationsForOwner(distributorId).ifPresent(recipients::add);
        memberships.findByDistributorIdAndStatusOrderByGrantedAtAsc(distributorId, DistributorMembershipStatus.ACTIVE)
            .forEach(m->recipients.add(m.getUser().getId()));
        recipients.remove(actorId);
        recipients.forEach(id->create(id,NotificationType.RELATIONSHIP_REQUEST_RECEIVED,"Retailer relationship request","A retailer requested a distributor relationship.","RELATIONSHIP_REQUEST",requestId));
    }
    @Transactional public void relationshipRequestDecision(UUID requestId, UUID requesterId, boolean approved) {
        NotificationType type=approved?NotificationType.RELATIONSHIP_REQUEST_APPROVED:NotificationType.RELATIONSHIP_REQUEST_REJECTED;
        create(requesterId,type,approved?"Relationship request accepted":"Relationship request declined",approved?"The distributor accepted your relationship request.":"The distributor declined your relationship request.","RELATIONSHIP_REQUEST",requestId);
    }
    @Transactional public void orderActionRequestReceived(UUID requestId, UUID distributorId, UUID actorId, OrderActionRequestType requestType) {
        Set<UUID> recipients=new LinkedHashSet<>();
        notificationsForOwner(distributorId).ifPresent(recipients::add);
        memberships.findByDistributorIdAndStatusOrderByGrantedAtAsc(distributorId, DistributorMembershipStatus.ACTIVE)
            .forEach(membership->recipients.add(membership.getUser().getId()));
        recipients.remove(actorId);
        boolean cancellation=requestType==OrderActionRequestType.CANCELLATION;
        NotificationType type=cancellation?NotificationType.CANCELLATION_REQUEST_RECEIVED:NotificationType.RETURN_REQUEST_RECEIVED;
        String title=cancellation?"Order cancellation requested":"Order return requested";
        String message=cancellation?"A retailer requested cancellation of an order.":"A retailer requested return of a delivered order.";
        recipients.forEach(recipient->create(recipient,type,title,message,"ORDER_ACTION_REQUEST",requestId));
    }
    @Transactional public void orderActionRequestDecision(UUID requestId, UUID requesterId,
        OrderActionRequestType requestType, boolean approved) {
        boolean cancellation=requestType==OrderActionRequestType.CANCELLATION;
        NotificationType type=cancellation
            ? approved?NotificationType.CANCELLATION_REQUEST_APPROVED:NotificationType.CANCELLATION_REQUEST_REJECTED
            : approved?NotificationType.RETURN_REQUEST_APPROVED:NotificationType.RETURN_REQUEST_REJECTED;
        String title=(cancellation?"Cancellation":"Return")+" request "+(approved?"approved":"rejected");
        String message="The Order Desk "+(approved?"approved":"rejected")+" your "+(cancellation?"cancellation":"return")+" request.";
        create(requesterId,type,title,message,"ORDER_ACTION_REQUEST",requestId);
    }
    private Optional<UUID> notificationsForOwner(UUID distributorId) {
        return businesses.findById(distributorId).map(b->b.getCreatedBy().getId());
    }
    private Optional<UUID> orderRetailerOwner(Order order) {
        return retailerMemberships.findFirstByRetailerIdAndRole(order.getRetailer().getId(), RetailerMembershipRole.RETAILER_OWNER)
            .map(membership->membership.getUser().getId());
    }
    private void create(UUID recipient, NotificationType type, String title, String message, String resourceType, UUID resourceId) {
        String dedupe=type.name()+":"+resourceId+":"+recipient;
        notifications.insertIfAbsent(UUID.randomUUID(),recipient,type.name(),title,message,resourceType,resourceId,dedupe,Instant.now());
    }
}
