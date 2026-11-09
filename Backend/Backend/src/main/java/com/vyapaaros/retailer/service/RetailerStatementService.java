package com.vyapaaros.retailer.service;

import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.entity.RetailerDistributorRelationship.RelationshipStatus;
import com.vyapaaros.distributor.repository.RetailerDistributorRelationshipRepository;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.order.entity.Order;
import com.vyapaaros.order.entity.OrderStatus;
import com.vyapaaros.order.repository.OrderRepository;
import com.vyapaaros.retailer.dto.RetailerStatementResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetailerStatementService {
    private static final ZoneOffset UTC = ZoneOffset.UTC;
    private static final int MAX_RANGE_DAYS = 366;
    private static final List<OrderStatus> ACTIVITY_STATUSES = List.of(
        OrderStatus.SUBMITTED, OrderStatus.CREDIT_HOLD, OrderStatus.APPROVED, OrderStatus.TALLY_EXPORTED
    );

    private final RetailerAuthorizationService retailerAuthorization;
    private final DistributorAuthorizationService distributorAuthorization;
    private final OrderRepository orders;
    private final RetailerDistributorRelationshipRepository relationships;

    public RetailerStatementService(RetailerAuthorizationService retailerAuthorization,
        DistributorAuthorizationService distributorAuthorization, OrderRepository orders,
        RetailerDistributorRelationshipRepository relationships) {
        this.retailerAuthorization = retailerAuthorization;
        this.distributorAuthorization = distributorAuthorization;
        this.orders = orders;
        this.relationships = relationships;
    }

    @Transactional(readOnly = true)
    public DistributorSummary currentDistributor(Authentication authentication, UUID retailerId) {
        retailerAuthorization.requireMembership(userId(authentication), retailerId);
        return relationships.findFirstByRetailerIdAndStatusOrderByStartedAtDesc(retailerId, RelationshipStatus.ACTIVE)
            .map(relationship -> new DistributorSummary(relationship.getDistributor().getId(), relationship.getDistributor().getBusinessName()))
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public RetailerStatementResponse statement(Authentication authentication, UUID retailerId, UUID distributorId,
        LocalDate requestedFrom, LocalDate requestedTo) {
        UUID userId = userId(authentication);
        // retailerId is a selector only; membership and active relationship are verified server-side.
        retailerAuthorization.requireMembership(userId, retailerId);
        distributorAuthorization.requireActiveRetailerLink(retailerId, distributorId);
        DistributorBusiness distributor = distributorAuthorization.requireCurrentDistributorForRetailer(retailerId);
        if (!distributor.getId().equals(distributorId)) throw denied();

        LocalDate today = LocalDate.now(UTC);
        LocalDate to = requestedTo == null ? today : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        if (from.isAfter(to)) throw new IllegalArgumentException("Start date must be on or before end date.");
        long days = Duration.between(from.atStartOfDay(UTC), to.plusDays(1).atStartOfDay(UTC)).toDays();
        if (days > MAX_RANGE_DAYS) throw new IllegalArgumentException("Date range cannot exceed 366 days.");
        if (to.isAfter(today)) throw new IllegalArgumentException("End date cannot be in the future.");

        Instant start = from.atStartOfDay(UTC).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(UTC).toInstant();
        List<Order> rows = orders.findStatementOrders(retailerId, distributorId, start, end);
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        rows.stream().filter(order -> ACTIVITY_STATUSES.contains(order.getStatus()))
            .forEach(order -> totals.merge(order.getCurrency(), order.getFinalAmount(), BigDecimal::add));
        List<RetailerStatementResponse.CurrencyTotal> currencyTotals = new ArrayList<>();
        totals.forEach((currency, amount) -> currencyTotals.add(
            new RetailerStatementResponse.CurrencyTotal(currency, amount)));
        return RetailerStatementResponse.from(distributor, from, to, rows, currencyTotals);
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof UUID id)) throw denied();
        return id;
    }

    private AccessDeniedException denied() { return new AccessDeniedException("Retailer statement access denied."); }

    public record DistributorSummary(UUID id, String businessName) { }
}
