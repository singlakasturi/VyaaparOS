package com.vyapaaros.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vyapaaros.distributor.entity.DistributorBusiness;
import com.vyapaaros.distributor.service.DistributorAuthorizationService;
import com.vyapaaros.order.entity.*;
import com.vyapaaros.order.repository.OrderRepository;
import com.vyapaaros.retailer.entity.Retailer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class OrderInvoiceServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderScopeService scope = mock(OrderScopeService.class);
    private final DistributorAuthorizationService distributorAuth = mock(DistributorAuthorizationService.class);
    private final OrderInvoiceService service = new OrderInvoiceService(orders, scope, distributorAuth);
    private final UUID orderId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final Order order = new Order();

    @BeforeEach void prepareOrder() {
        Retailer retailer = new Retailer();
        retailer.setBusinessName("Shop <One>"); retailer.setOwnerName("Rita");
        retailer.setAddress("12 Main Rd"); retailer.setCity("Jaipur"); retailer.setState("Rajasthan");
        retailer.setPincode("302001"); retailer.setContactPhone("1234567890"); retailer.setContactEmail("shop@example.test");
        retailer.setGstin("GST-OPTIONAL");
        DistributorBusiness distributor = new DistributorBusiness(); distributor.setBusinessName("Supply & Co");
        order.setId(orderId); order.setOrderNumber("VY-2026-001"); order.setRetailer(retailer); order.setDistributor(distributor);
        order.setStatus(OrderStatus.APPROVED); order.setFulfillmentStatus(FulfillmentStatus.SHIPPED); order.setTrackingReference("TRK-42");
        order.setCreatedAt(Instant.parse("2026-05-10T12:30:00Z")); order.setCurrency("INR");
        order.setBaseAmount(new BigDecimal("1000.00")); order.setDiscountAmount(new BigDecimal("100.00")); order.setFinalAmount(new BigDecimal("900.00"));
        OrderItem item = new OrderItem(); item.setProductName("Rice <Premium>"); item.setProductSku("RICE-1");
        item.setPaidCases(2); item.setFreeCases(1); item.setTotalCases(3); item.setUnitPricePerCase(new BigDecimal("500.00"));
        item.setCurrency("INR"); item.setBaseAmount(new BigDecimal("1000.00")); item.setDiscountAmount(new BigDecimal("100.00"));
        item.setFinalAmount(new BigDecimal("900.00")); item.setSchemeName("Summer offer"); item.setSchemeType("BUY_X_GET_Y_FREE");
        order.addItem(item);
        when(orders.findDetailed(orderId)).thenReturn(Optional.of(order));
        when(scope.userId(any())).thenReturn(actorId);
    }

    @Test void rendersPersistedSnapshotsAndEscapesProfileAndItemTextWithoutChangingOrder() {
        Authentication auth = auth("ROLE_RETAILER");
        String html = service.retailerInvoice(auth, orderId);
        assertTrue(html.contains("Supply &amp; Co"));
        assertTrue(html.contains("Shop &lt;One&gt;"));
        assertTrue(html.contains("Rice &lt;Premium&gt;"));
        assertTrue(html.contains("Summer offer"));
        assertTrue(html.contains("INR 1000.00"));
        assertTrue(html.contains("INR 900.00"));
        assertTrue(html.contains("Current order status"));
        assertTrue(html.contains("SHIPPED"));
        assertTrue(html.contains("Separate tax amount: unavailable"));
        verify(scope).requireHistoricalOrderAccess(actorId, order.getRetailer().getId(), auth);
        assertEquals(OrderStatus.APPROVED, order.getStatus());
        assertEquals(FulfillmentStatus.SHIPPED, order.getFulfillmentStatus());
        assertEquals(new BigDecimal("900.00"), order.getFinalAmount());
    }

    @Test void handlesMissingOptionalProfileAndSchemeFields() {
        Retailer retailer = order.getRetailer(); retailer.setOwnerName(null); retailer.setCity(null); retailer.setState(null);
        retailer.setPincode(null); retailer.setGstin(null); retailer.setContactPhone(null); retailer.setContactEmail(null);
        order.getItems().get(0).setSchemeName(null);
        String html = service.retailerInvoice(auth("ROLE_RETAILER"), orderId);
        assertTrue(html.contains("12 Main Rd"));
        assertFalse(html.contains("GSTIN ·"));
        assertFalse(html.contains("Recorded offer:"));
    }

    @Test void orderDeskRequiresRoleAndActiveDistributorMembership() {
        Authentication auth = auth("ROLE_ORDER_DESK");
        String html = service.orderDeskInvoice(auth, orderId);
        assertTrue(html.contains("VY-2026-001"));
        verify(distributorAuth).requireOrderDeskAccess(actorId, order.getDistributor().getId());
        Authentication wrongRole = auth("ROLE_RETAILER");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.orderDeskInvoice(wrongRole, orderId));
        verify(distributorAuth).requireOrderDeskAccess(actorId, order.getDistributor().getId());
    }

    private Authentication auth(String role) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(actorId);
        doReturn(java.util.List.of(new SimpleGrantedAuthority(role))).when(auth).getAuthorities();
        return auth;
    }
}
