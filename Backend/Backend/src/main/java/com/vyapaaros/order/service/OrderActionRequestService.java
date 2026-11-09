package com.vyapaaros.order.service;

import com.vyapaaros.common.exception.ResourceConflictException;
import com.vyapaaros.common.exception.ResourceNotFoundException;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.notification.service.NotificationService;
import com.vyapaaros.order.dto.OrderActionRequestResponse;
import com.vyapaaros.order.entity.*;
import com.vyapaaros.order.repository.OrderActionRequestRepository;
import com.vyapaaros.order.repository.OrderRepository;
import com.vyapaaros.order.repository.OrderStatusHistoryRepository;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderActionRequestService {
    private static final Set<OrderStatus> CANCELLABLE_ORDER_STATUSES = Set.of(OrderStatus.SUBMITTED, OrderStatus.CREDIT_HOLD, OrderStatus.APPROVED);
    private final OrderActionRequestRepository requests;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final UserRepository users;
    private final OrderScopeService retailerScope;
    private final DistributorAuthorizationService distributorAuthorization;
    private final InventoryRestorationService restoration;
    private final NotificationService notifications;

    public OrderActionRequestService(OrderActionRequestRepository requests, OrderRepository orders,
        OrderStatusHistoryRepository history, UserRepository users, OrderScopeService retailerScope,
        DistributorAuthorizationService distributorAuthorization, InventoryRestorationService restoration,
        NotificationService notifications) {
        this.requests=requests; this.orders=orders; this.history=history; this.users=users; this.retailerScope=retailerScope;
        this.distributorAuthorization=distributorAuthorization; this.restoration=restoration; this.notifications=notifications;
    }

    @Transactional
    public OrderActionRequestResponse requestCancellation(Authentication auth, UUID orderId, String reason) {
        return create(auth,orderId,OrderActionRequestType.CANCELLATION,reason);
    }

    @Transactional
    public OrderActionRequestResponse requestReturn(Authentication auth, UUID orderId, String reason) {
        return create(auth,orderId,OrderActionRequestType.RETURN,reason);
    }

    @Transactional(readOnly=true)
    public List<OrderActionRequestResponse> retailerRequests(Authentication auth, UUID orderId) {
        UUID userId=retailerScope.userId(auth);
        Order order=orders.findById(orderId).orElseThrow(()->new ResourceNotFoundException("Order not found."));
        retailerScope.requireHistoricalOrderAccess(userId,order.getRetailer().getId(),auth);
        return requests.findByOrderIdAndRequestedByIdOrderByCreatedAtDesc(orderId,userId)
            .stream().map(OrderActionRequestResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public Page<OrderActionRequestResponse> orderDeskRequests(Authentication auth, OrderActionRequestStatus status,
        OrderActionRequestType type, UUID orderId, int page, int size) {
        Set<UUID> distributorIds=authorizedDistributorIds(auth);
        Pageable pageable=PageRequest.of(Math.max(0,page),Math.min(Math.max(1,size),100),
            Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")));
        return requests.findForOrderDesk(distributorIds,status,type,orderId,pageable).map(OrderActionRequestResponse::from);
    }

    @Transactional
    public OrderActionRequestResponse approve(Authentication auth, UUID requestId, String comment) {
        return decide(auth,requestId,comment,true);
    }

    @Transactional
    public OrderActionRequestResponse reject(Authentication auth, UUID requestId, String comment) {
        return decide(auth,requestId,comment,false);
    }

    private OrderActionRequestResponse create(Authentication auth, UUID orderId, OrderActionRequestType type, String rawReason) {
        UUID userId=retailerScope.userId(auth);
        Order order=orders.findForUpdate(orderId).orElseThrow(()->new ResourceNotFoundException("Order not found."));
        retailerScope.requireHistoricalOrderAccess(userId,order.getRetailer().getId(),auth);
        String reason=normalizeText(rawReason,"Reason",500);
        if(type==OrderActionRequestType.CANCELLATION && !cancellationEligible(order)) {
            throw new ResourceConflictException("This order is no longer eligible for a cancellation request.");
        }
        if(type==OrderActionRequestType.RETURN) {
            if(reason==null || reason.isBlank()) throw new ResourceConflictException("A return reason is required.");
            if(!returnEligible(order)) throw new ResourceConflictException("A return request is only available for delivered orders.");
        }
        if(requests.existsByOrderIdAndRequestTypeAndStatusIn(orderId,type,
            List.of(OrderActionRequestStatus.PENDING,OrderActionRequestStatus.APPROVED))) {
            throw new ResourceConflictException("An active or approved request of this type already exists for this order.");
        }
        User requester=users.findById(userId).orElseThrow(()->new ResourceNotFoundException("User not found."));
        Instant now=Instant.now();
        OrderActionRequest request=new OrderActionRequest();
        request.setOrder(order); request.setRequestType(type); request.setStatus(OrderActionRequestStatus.PENDING);
        request.setRequestedBy(requester); request.setReason(reason); request.setCreatedAt(now);
        request=requests.save(request);
        saveRequestHistory(order,request,OrderActionRequestStatus.PENDING,requestEvent(type,false),requester,
            reason==null?"Request created.":"Request created: "+reason,now);
        if(notifications!=null) notifications.orderActionRequestReceived(request.getId(),order.getDistributor().getId(),userId,type);
        return OrderActionRequestResponse.from(request);
    }

    private OrderActionRequestResponse decide(Authentication auth, UUID requestId, String rawComment, boolean approve) {
        UUID actorId=authenticatedUserId(auth);
        Set<UUID> distributorIds=authorizedDistributorIds(auth);
        OrderActionRequest initial=requests.findById(requestId).orElseThrow(()->new ResourceNotFoundException("Request not found."));
        Order orderForScope=initial.getOrder();
        if(!distributorIds.contains(orderForScope.getDistributor().getId())) throw new ResourceNotFoundException("Request not found.");
        distributorAuthorization.requireOrderDeskAccess(actorId,orderForScope.getDistributor().getId());

        Order order=orders.findForUpdate(orderForScope.getId()).orElseThrow(()->new ResourceNotFoundException("Order not found."));
        OrderActionRequest request=requests.findForUpdate(requestId).orElseThrow(()->new ResourceNotFoundException("Request not found."));
        if(!request.getOrder().getId().equals(order.getId())) throw new ResourceNotFoundException("Request not found.");
        if(request.getStatus()!=OrderActionRequestStatus.PENDING) throw new ResourceConflictException("This request has already been decided.");
        String comment=normalizeText(rawComment,"Decision comment",500);
        if(approve && request.getRequestType()==OrderActionRequestType.CANCELLATION) {
            if(!cancellationEligible(order)) throw new ResourceConflictException("The order has progressed and can no longer be cancelled.");
            User actor=users.findById(actorId).orElseThrow(()->new ResourceNotFoundException("User not found."));
            OrderStatus previous=order.getStatus();
            restoration.restore(order,actor,"Cancellation request approved.");
            Instant now=Instant.now();
            order.setStatus(OrderStatus.CANCELLED); order.setUpdatedAt(now); orders.save(order);
            saveOrderStatusHistory(order,previous,OrderStatus.CANCELLED,actor,"Cancellation request approved.",now);
        }
        if(approve && request.getRequestType()==OrderActionRequestType.RETURN && !returnEligible(order)) {
            throw new ResourceConflictException("A return request can only be approved for a delivered order.");
        }
        User actor=users.findById(actorId).orElseThrow(()->new ResourceNotFoundException("User not found."));
        OrderActionRequestStatus decision=approve?OrderActionRequestStatus.APPROVED:OrderActionRequestStatus.REJECTED;
        Instant now=Instant.now();
        request.setStatus(decision); request.setDecidedBy(actor); request.setDecidedAt(now); request.setDecisionComment(comment);
        requests.save(request);
        String message="Request "+decision.name().toLowerCase(Locale.ROOT)+"."+(comment==null?"":" "+comment);
        saveRequestHistory(order,request,decision,requestEvent(request.getRequestType(),true),actor,message,now);
        if(notifications!=null) notifications.orderActionRequestDecision(requestId,request.getRequestedBy().getId(),request.getRequestType(),approve);
        return OrderActionRequestResponse.from(request);
    }

    private void saveRequestHistory(Order order, OrderActionRequest request, OrderActionRequestStatus status,
        OrderHistoryEventType type, User actor, String reason, Instant at) {
        OrderStatusHistory event=new OrderStatusHistory();
        event.setOrder(order); event.setPreviousStatus(order.getStatus()); event.setNewStatus(order.getStatus());
        event.setEventType(type); event.setRequestId(request.getId()); event.setRequestStatus(status.name());
        event.setChangedBy(actor); event.setReason(reason); event.setCreatedAt(at); history.save(event);
    }

    private void saveOrderStatusHistory(Order order, OrderStatus previous, OrderStatus next, User actor, String reason, Instant at) {
        OrderStatusHistory event=new OrderStatusHistory();
        event.setOrder(order); event.setPreviousStatus(previous); event.setNewStatus(next); event.setEventType(OrderHistoryEventType.ORDER_STATUS);
        event.setChangedBy(actor); event.setReason(reason); event.setCreatedAt(at); history.save(event);
    }

    static boolean cancellationEligible(Order order) {
        return CANCELLABLE_ORDER_STATUSES.contains(order.getStatus())
            && (order.getFulfillmentStatus()==FulfillmentStatus.NOT_STARTED || order.getFulfillmentStatus()==FulfillmentStatus.PREPARING);
    }

    static boolean returnEligible(Order order) {
        return (order.getStatus()==OrderStatus.APPROVED || order.getStatus()==OrderStatus.TALLY_EXPORTED)
            && order.getFulfillmentStatus()==FulfillmentStatus.DELIVERED;
    }

    private OrderHistoryEventType requestEvent(OrderActionRequestType type, boolean decision) {
        return switch(type) {
            case CANCELLATION -> decision?OrderHistoryEventType.CANCELLATION_DECISION:OrderHistoryEventType.CANCELLATION_REQUEST;
            case RETURN -> decision?OrderHistoryEventType.RETURN_DECISION:OrderHistoryEventType.RETURN_REQUEST;
        };
    }

    private String normalizeText(String value, String field, int maxLength) {
        if(value==null) return null;
        String normalized=value.trim();
        if(normalized.isEmpty()) return null;
        if(normalized.length()>maxLength || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new ResourceConflictException(field+" must be at most "+maxLength+" characters and cannot contain control characters.");
        }
        return normalized;
    }

    private UUID authenticatedUserId(Authentication auth) {
        if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UUID id)) throw new AccessDeniedException("Authentication required.");
        return id;
    }
    private Set<UUID> authorizedDistributorIds(Authentication auth) {
        return distributorAuthorization.activeOrderDeskBusinessIds(authenticatedUserId(auth));
    }
}
