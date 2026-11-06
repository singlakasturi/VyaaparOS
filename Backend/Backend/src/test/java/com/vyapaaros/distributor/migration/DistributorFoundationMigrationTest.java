package com.vyapaaros.distributor.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DistributorFoundationMigrationTest {
    @Autowired private JdbcTemplate jdbc;

    @Test
    void developmentBackfillAssignsSeedDataAndPreservesOrdersAndRolesSafely() {
        assertEquals(1, jdbc.queryForObject("select count(*) from distributor_businesses where id = '60000000-0000-0000-0000-000000000001'", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from retailer_distributor_relationships where status = 'ACTIVE'", Integer.class));
        assertEquals(3, jdbc.queryForObject("select count(*) from products where distributor_id = '60000000-0000-0000-0000-000000000001'", Integer.class));
        assertEquals(3, jdbc.queryForObject("select count(*) from inventory where distributor_id = '60000000-0000-0000-0000-000000000001'", Integer.class));
        assertEquals(3, jdbc.queryForObject("select count(*) from product_prices where distributor_id = '60000000-0000-0000-0000-000000000001'", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from product_schemes where distributor_id = '60000000-0000-0000-0000-000000000001'", Integer.class));
        assertEquals(12, jdbc.queryForObject("select count(*) from orders where distributor_id = '60000000-0000-0000-0000-000000000001'", Integer.class));
        assertEquals(21, jdbc.queryForObject("select count(*) from order_items i join orders o on o.id=i.order_id", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from roles where name in ('RETAILER','ORDER_DESK')", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from roles where name in ('ADMIN','SALESPERSON')", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from user_roles ur join roles r on r.id=ur.role_id where r.name in ('ADMIN','SALESPERSON','ORDER_DESK')", Integer.class));
    }

    @Test
    @Transactional
    @Rollback
    void databaseRejectsSecondActiveDistributorRelationshipForRetailer() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
            insert into retailer_distributor_relationships(id, retailer_id, distributor_id, status, started_by)
            select ?, r.retailer_id, r.distributor_id, 'ACTIVE', r.started_by
            from retailer_distributor_relationships r
            where r.status = 'ACTIVE'
            limit 1
            """, UUID.randomUUID()));
    }

    @Test
    @Transactional
    @Rollback
    void endedRelationshipsCanBeRetainedAsHistory() {
        UUID relationshipId = UUID.randomUUID();
        jdbc.update("""
            insert into retailer_distributor_relationships(id, retailer_id, distributor_id, status, started_by, started_at, ended_at)
            select ?, retailer_id, distributor_id, 'ENDED', started_by, started_at, now()
            from retailer_distributor_relationships where status = 'ACTIVE' limit 1
            """, relationshipId);
        assertEquals(1, jdbc.queryForObject("select count(*) from retailer_distributor_relationships where id = ? and status = 'ENDED'", Integer.class, relationshipId));
        assertEquals(12, jdbc.queryForObject("select count(*) from orders", Integer.class));
    }
}
