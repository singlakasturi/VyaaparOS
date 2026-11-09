package com.vyapaaros.order.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OrderDeskSearchQueryIntegrationTest {
    private static final UUID SEEDED_DISTRIBUTOR = UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Autowired private OrderRepository orders;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void orderIdSearchRunsAgainstPostgresAndReturnsMatchingDistributorOrder() {
        UUID existingId = jdbc.queryForObject(
            "select id from orders where distributor_id = ? limit 1", UUID.class, SEEDED_DISTRIBUTOR);

        var page = orders.searchByDistributorIds(Set.of(SEEDED_DISTRIBUTOR), null, null,
            OrderRepository.EARLIEST_ORDER_DATE, OrderRepository.LATEST_ORDER_DATE,
            existingId.toString().substring(0, 8), PageRequest.of(0, 10));

        assertTrue(page.getContent().stream().anyMatch(order -> order.getId().equals(existingId)));
        assertFalse(orders.countByStatusForDistributors(Set.of(SEEDED_DISTRIBUTOR)).isEmpty());
        assertFalse(orders.findRecentByDistributorIds(Set.of(SEEDED_DISTRIBUTOR), PageRequest.of(0, 5)).isEmpty());
    }
}
