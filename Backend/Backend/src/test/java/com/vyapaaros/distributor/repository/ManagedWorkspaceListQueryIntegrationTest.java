package com.vyapaaros.distributor.repository;

import com.vyapaaros.catalog.repository.ProductRepository;
import com.vyapaaros.inventory.repository.InventoryRepository;
import com.vyapaaros.schemes.repository.ProductSchemeRepository;
import com.vyapaaros.order.repository.OrderRepository;
import com.vyapaaros.distributor.controller.DistributorCatalogManagementController;
import com.vyapaaros.inventory.controller.DistributorInventoryController;
import com.vyapaaros.schemes.controller.SchemeController;
import com.vyapaaros.order.controller.OrderDeskController;
import com.vyapaaros.order.entity.OrderStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ManagedWorkspaceListQueryIntegrationTest {
    private static final UUID SEEDED_DISTRIBUTOR = UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Autowired ProductRepository products;
    @Autowired InventoryRepository inventory;
    @Autowired ProductSchemeRepository schemes;
    @Autowired OrderRepository orders;
    @Autowired DistributorCatalogManagementController catalogController;
    @Autowired DistributorInventoryController inventoryController;
    @Autowired SchemeController schemeController;
    @Autowired OrderDeskController orderDeskController;
    @Autowired JdbcTemplate jdbc;

    @Test
    void nullableSearchQueriesWorkAgainstPostgres() {
        assertTrue(products.findManagedByDistributor(SEEDED_DISTRIBUTOR, null, null).size() >= 3);
        assertTrue(inventory.searchForDistributor(SEEDED_DISTRIBUTOR, null, false, PageRequest.of(0, 20)).getTotalElements() >= 3);
        assertEquals(2, schemes.searchManaged(SEEDED_DISTRIBUTOR, null, null).size());
        assertTrue(orders.searchByDistributorIds(Set.of(SEEDED_DISTRIBUTOR), null, null,
            Instant.parse("0001-01-01T00:00:00Z"), Instant.parse("9999-12-31T23:59:59Z"), null,
            PageRequest.of(0, 20)).getTotalElements() >= 13);

    }

    @Test
    void authenticatedWorkspaceControllersLoadAllAffectedPages() {
        UUID ownerId = jdbc.queryForObject("select created_by from distributor_businesses where id = ?",
            UUID.class, SEEDED_DISTRIBUTOR);
        Authentication auth = new UsernamePasswordAuthenticationToken(ownerId, "", java.util.List.of());

        assertTrue(catalogController.products(null, null, auth).size() >= 3);
        assertTrue(inventoryController.list(null, false, 0, 20, auth).getTotalElements() >= 3);
        assertEquals(2, schemeController.managed(null, null, auth).size());
        assertTrue(orderDeskController.list(OrderStatus.SUBMITTED, null, null, null, null, 0, 20, auth)
            .getTotalElements() > 0);
        assertTrue(orderDeskController.exports(0, 20, auth).getTotalElements() >= 0);
    }

    @Test
    void nullableSearchQueriesAlsoWorkWhenSearchIsProvided() {
        assertTrue(products.findManagedByDistributor(SEEDED_DISTRIBUTOR, "biscuit", null).size() > 0);
        assertTrue(inventory.searchForDistributor(SEEDED_DISTRIBUTOR, "biscuit", false, PageRequest.of(0, 20)).getTotalElements() > 0);
        assertTrue(schemes.searchManaged(SEEDED_DISTRIBUTOR, "case", null).size() > 0);
        assertTrue(orders.searchByDistributorIds(Set.of(SEEDED_DISTRIBUTOR), null, null,
            Instant.parse("0001-01-01T00:00:00Z"), Instant.parse("9999-12-31T23:59:59Z"), "retailer",
            PageRequest.of(0, 20)).getTotalElements() > 0);
    }
}
