package com.vyapaaros.order.service;

import com.vyapaaros.common.exception.*;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.order.dto.*;
import com.vyapaaros.order.entity.*;
import com.vyapaaros.order.repository.*;
import com.vyapaaros.user.entity.User;
import com.vyapaaros.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class OrderDeskService {
    private final OrderRepository orders;
    private final UserRepository users;
    private final OrderStatusHistoryRepository history;
    private final InventoryRestorationService restoration;
    private final TallyExportBatchRepository batches;
    private final TallyExportBatchOrderRepository batchOrders;
    private final DistributorAuthorizationService authorization;

    public OrderDeskService(OrderRepository orders, UserRepository users,
        OrderStatusHistoryRepository history, InventoryRestorationService restoration,
        TallyExportBatchRepository batches, TallyExportBatchOrderRepository batchOrders) {
        this(orders, users, history, restoration, batches, batchOrders,
            new DistributorAuthorizationService(null, null, null));
    }

    @Autowired
    public OrderDeskService(OrderRepository orders, UserRepository users,
        OrderStatusHistoryRepository history, InventoryRestorationService restoration,
        TallyExportBatchRepository batches, TallyExportBatchOrderRepository batchOrders,
        DistributorAuthorizationService authorization) {
        this.orders = orders; this.users = users; this.history = history; this.restoration = restoration;
        this.batches = batches; this.batchOrders = batchOrders;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Page<OrderDeskOrderResponse> list(Authentication auth, OrderStatus status, UUID retailerId,
        Instant startDate, Instant endDate, Pageable pageable) {
        Set<UUID> distributorIds = authorizedDistributorIds(auth);
        return orders.searchByDistributorIds(distributorIds, status, retailerId, startDate, endDate, pageable)
            .map(OrderDeskOrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderDeskOrderResponse get(Authentication auth, UUID id) {
        authorizedDistributorIds(auth);
        Order order = findDetailed(id);
        requireOrderAccess(auth, order);
        return OrderDeskOrderResponse.from(order);
    }

    @Transactional
    public OrderDeskOrderResponse approve(Authentication auth, UUID id, String reason) {
        authorizedDistributorIds(auth);
        User actor = actor(auth);
        Order order = findLocked(id);
        requireOrderAccess(auth, order);
        if (order.getStatus() == OrderStatus.CREDIT_HOLD && (reason == null || reason.isBlank())) {
            throw new ResourceConflictException("A credit override reason is required.");
        }
        if (order.getStatus() != OrderStatus.SUBMITTED && order.getStatus() != OrderStatus.CREDIT_HOLD) {
            throw new ResourceConflictException("Order cannot be approved from its current status.");
        }
        transition(order, OrderStatus.APPROVED, actor, reason);
        return OrderDeskOrderResponse.from(order);
    }

    @Transactional
    public OrderDeskOrderResponse reject(Authentication auth, UUID id, String reason) {
        authorizedDistributorIds(auth);
        User actor = actor(auth);
        Order order = findLocked(id);
        requireOrderAccess(auth, order);
        if (reason == null || reason.isBlank()) throw new ResourceConflictException("A rejection reason is required.");
        if (order.getStatus() != OrderStatus.SUBMITTED && order.getStatus() != OrderStatus.CREDIT_HOLD) {
            throw new ResourceConflictException("Order cannot be rejected from its current status.");
        }
        restoration.restore(order);
        transition(order, OrderStatus.REJECTED, actor, reason);
        return OrderDeskOrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> history(Authentication auth, UUID id) {
        authorizedDistributorIds(auth);
        Order order = orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found."));
        requireOrderAccess(auth, order);
        return history.findByOrderIdOrderByCreatedAtDesc(id).stream().map(OrderStatusHistoryResponse::from).toList();
    }

    @Transactional
    public ExportedCsv export(Authentication auth, OrderDeskExportRequest request) {
        User actor = actor(auth);
        Set<UUID> authorizedIds = authorizedDistributorIds(auth);
        List<Order> selected = selectForExport(request, authorizedIds);
        if (selected.isEmpty()) throw new ResourceConflictException("No approved orders are available for export.");
        selected.forEach(order -> requireOrderAccess(auth, order));
        for (Order order : selected) {
            if (order.getStatus() != OrderStatus.APPROVED) {
                throw new ResourceConflictException("Only approved orders can be exported.");
            }
            if (batchOrders.existsByOrderId(order.getId())) {
                throw new ResourceConflictException("Order has already been exported.");
            }
        }
        String filename = "tally-orders-" + LocalDate.now() + "-" + UUID.randomUUID() + ".csv";
        TallyExportBatch batch = new TallyExportBatch();
        UUID distributorId = selected.get(0).getDistributor().getId();
        if (selected.stream().anyMatch(order -> !distributorId.equals(order.getDistributor().getId()))) {
            throw new ResourceConflictException("An export batch must contain orders from one distributor.");
        }
        batch.setDistributor(selected.get(0).getDistributor());
        batch.setFilename(filename); batch.setCreatedBy(actor); batch.setCreatedAt(Instant.now()); batch.setOrderCount(selected.size());
        batch = batches.save(batch);
        final TallyExportBatch savedBatch = batch;
        selected.forEach(order -> {
            batchOrders.save(new TallyExportBatchOrder(savedBatch.getId(), order.getId()));
            transition(order, OrderStatus.TALLY_EXPORTED, actor, "Tally staging export " + savedBatch.getId());
        });
        return new ExportedCsv(filename, TallyCsvWriter.write(selected));
    }

    @Transactional(readOnly = true)
    public Page<TallyExportBatchResponse> exports(Authentication auth, Pageable pageable) {
        Set<UUID> distributorIds = authorizedDistributorIds(auth);
        return batches.findByDistributorIdIn(distributorIds, pageable)
            .map(batch -> TallyExportBatchResponse.from(batch, orderNumbers(batch.getId())));
    }

    @Transactional(readOnly = true)
    public TallyExportBatchResponse exportDetails(Authentication auth, UUID id) {
        authorizedDistributorIds(auth);
        TallyExportBatch batch = batches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Export not found."));
        requireBusinessAccess(auth, batch.getDistributor().getId());
        return TallyExportBatchResponse.from(batch, orderNumbers(id));
    }

    private List<Order> selectForExport(OrderDeskExportRequest request, Set<UUID> distributorIds) {
        if (request.orderIds() != null && !request.orderIds().isEmpty()) {
            if (request.orderIds().size() > 500) throw new ResourceConflictException("Export is limited to 500 orders.");
            return request.orderIds().stream().map(this::findLocked).toList();
        }
        Pageable limit = PageRequest.of(0, 500, Sort.by("createdAt").ascending());
        return orders.searchByDistributorIds(distributorIds, OrderStatus.APPROVED, null, request.startDate(), request.endDate(), limit).getContent()
            .stream().map(order -> findLocked(order.getId())).toList();
    }

    private void transition(Order order, OrderStatus next, User actor, String reason) {
        OrderStatus previous = order.getStatus();
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setOrder(order); entry.setPreviousStatus(previous); entry.setNewStatus(next);
        entry.setChangedBy(actor); entry.setReason(reason); entry.setCreatedAt(Instant.now());
        history.save(entry);
        order.setStatus(next); order.setUpdatedAt(Instant.now());
        orders.save(order);
    }

    private Order findDetailed(UUID id) {
        return orders.findDetailed(id).orElseThrow(() -> new ResourceNotFoundException("Order not found."));
    }
    private Order findLocked(UUID id) {
        return orders.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Order not found."));
    }
    private User actor(Authentication auth) {
        if (authorization == null || auth == null || !auth.isAuthenticated()
            || !(auth.getPrincipal() instanceof UUID id)) throw new AccessDeniedException("Authentication required.");
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }
    private Set<UUID> authorizedDistributorIds(Authentication auth) {
        if (authorization == null || auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UUID userId)) {
            throw new AccessDeniedException("Order desk access denied.");
        }
        return authorization.activeOrderDeskBusinessIds(userId);
    }
    private void requireBusinessAccess(Authentication auth, UUID distributorId) {
        if (authorization == null || auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UUID userId)) {
            throw new AccessDeniedException("Order desk access denied.");
        }
        authorization.requireOrderDeskAccess(userId, distributorId);
    }
    private void requireOrderAccess(Authentication auth, Order order) {
        requireBusinessAccess(auth, order.getDistributor().getId());
    }
    private List<String> orderNumbers(UUID batchId) {
        List<UUID> ids = batchOrders.findByBatchId(batchId).stream().map(TallyExportBatchOrder::getOrderId).toList();
        return orders.findAllById(ids).stream().map(Order::getOrderNumber).sorted().toList();
    }
    public record ExportedCsv(String filename, byte[] content) {}
}
