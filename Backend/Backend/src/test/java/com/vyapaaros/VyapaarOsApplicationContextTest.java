package com.vyapaaros;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class VyapaarOsApplicationContextTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void applicationContextStarts() {
    }

    @Test
    void legacySalespersonAssignmentAndWorkspaceRoutesAreNotExposed() {
        var paths = handlerMapping.getHandlerMethods().keySet().stream()
            .flatMap(mapping -> mapping.getPatternValues().stream())
            .toList();

        assertFalse(paths.stream().anyMatch(path -> path.startsWith("/api/salesperson/retailers")));
        assertFalse(paths.stream().anyMatch(path -> path.startsWith("/api/retailers/assigned")));
        assertFalse(paths.stream().anyMatch(path -> path.contains("/salespeople")));
        assertTrue(paths.contains("/api/distributor/me"));
        assertTrue(paths.contains("/api/distributor/me/staff"));
        assertTrue(paths.contains("/api/distributor/me/staff/{staffUserId}"));
        assertTrue(paths.contains("/api/orders/{orderId}/action-requests"));
        assertTrue(paths.contains("/api/orders/{orderId}/action-requests/cancellation"));
        assertTrue(paths.contains("/api/orders/{orderId}/action-requests/return"));
        assertTrue(paths.contains("/api/order-desk/action-requests"));
        assertTrue(paths.contains("/api/order-desk/action-requests/{requestId}/approve"));
        assertTrue(paths.contains("/api/order-desk/action-requests/{requestId}/reject"));
        assertTrue(paths.contains("/api/distributor/me/inventory/movements"));
        assertTrue(paths.contains("/api/distributor/me/inventory/products/{productId}/reconciliation"));
        assertTrue(paths.contains("/api/distributor/me/reports"));
        assertTrue(paths.contains("/api/distributor/me/reports/export"));
        assertTrue(paths.contains("/api/orders/{id}/invoice"));
        assertTrue(paths.contains("/api/order-desk/orders/{id}/invoice"));
    }
}
