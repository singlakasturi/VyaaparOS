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
    private static final UUID DEMO_ORDER_CLIENT_ID = UUID.fromString("24000000-0000-0000-0000-000000000001");

    @Test
    void developmentBackfillAssignsSeedDataAndPreservesOrdersAndRolesSafely() {
        assertEquals(1, jdbc.queryForObject("select count(*) from distributor_businesses where id = '60000000-0000-0000-0000-000000000001'", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from retailer_distributor_relationships where status = 'ACTIVE'", Integer.class));
        assertTrue(jdbc.queryForObject("select count(*) from products where distributor_id = '60000000-0000-0000-0000-000000000001'", Integer.class) >= 3);
        assertTrue(jdbc.queryForObject("select count(*) from inventory where distributor_id = '60000000-0000-0000-0000-000000000001'", Integer.class) >= 3);
        assertTrue(jdbc.queryForObject("select count(*) from product_prices where distributor_id = '60000000-0000-0000-0000-000000000001'", Integer.class) >= 3);
        assertEquals(2, jdbc.queryForObject("select count(*) from product_schemes where distributor_id = '60000000-0000-0000-0000-000000000001'", Integer.class));
        assertTrue(jdbc.queryForObject("select count(*) from orders where distributor_id = '60000000-0000-0000-0000-000000000001' and client_order_id is distinct from ?", Integer.class, DEMO_ORDER_CLIENT_ID) >= 12);
        assertTrue(jdbc.queryForObject("select count(*) from order_items i join orders o on o.id=i.order_id where o.client_order_id is distinct from ?", Integer.class, DEMO_ORDER_CLIENT_ID) >= 21);
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
        assertTrue(jdbc.queryForObject("select count(*) from orders where client_order_id is distinct from ?", Integer.class, DEMO_ORDER_CLIENT_ID) >= 12);
    }

    @Test
    @Transactional
    @Rollback
    void sameSkuAndCategorySlugAreAllowedAcrossBusinessesButNotWithinOneBusiness() {
        UUID secondBusiness=UUID.randomUUID(),secondCategory=UUID.randomUUID(),secondProduct=UUID.randomUUID();
        jdbc.update("""
            insert into distributor_businesses(id,business_name,created_by)
            select ?, 'Second Distributor', created_by from distributor_businesses where id='60000000-0000-0000-0000-000000000001'
            """,secondBusiness);
        String existingSlug=jdbc.queryForObject("select slug from product_categories where distributor_id='60000000-0000-0000-0000-000000000001' limit 1",String.class);
        String existingSku=jdbc.queryForObject("select sku from products where distributor_id='60000000-0000-0000-0000-000000000001' limit 1",String.class);
        jdbc.update("insert into product_categories(id,distributor_id,name,slug) values(?,?,?,?)",secondCategory,secondBusiness,"Other category",existingSlug);
        jdbc.update("""
            insert into products(id,distributor_id,sku,name,category_id,unit_name,unit_quantity,units_per_case)
            values(?,?,?,?,?,?,?,?)
            """,secondProduct,secondBusiness,existingSku,"Duplicate SKU in separate business",secondCategory,"case",1,1);
        assertEquals(2,jdbc.queryForObject("select count(*) from products where upper(sku)=upper(?)",Integer.class,existingSku));
        UUID duplicateProduct=UUID.randomUUID();
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("""
            insert into products(id,distributor_id,sku,name,category_id,unit_name,unit_quantity,units_per_case)
            values(?,?,?,?,?,?,?,?)
            """,duplicateProduct,secondBusiness,existingSku.toLowerCase(),"Duplicate in same business",secondCategory,"case",1,1));
    }

    @Test
    @Transactional
    @Rollback
    void inventoryAdjustmentLedgerEnforcesBusinessProductAndActorReferences() {
        UUID adjustmentId=UUID.randomUUID(),otherBusiness=UUID.randomUUID();
        jdbc.update("""
            insert into distributor_businesses(id,business_name,created_by)
            select ?, 'Second Distributor', created_by from distributor_businesses where id='60000000-0000-0000-0000-000000000001'
            """,otherBusiness);
        jdbc.update("""
            insert into inventory_adjustments(id,inventory_id,product_id,distributor_id,actor_id,quantity_delta,quantity_after,reason)
            select ?, i.id, i.product_id, i.distributor_id,
                   (select created_by from distributor_businesses where id=i.distributor_id), 2, i.available_cases + 2, 'Stock count'
            from inventory i where i.distributor_id='60000000-0000-0000-0000-000000000001' limit 1
            """,adjustmentId);
        assertEquals(1,jdbc.queryForObject("select count(*) from inventory_adjustments where id=? and reason='Stock count'",Integer.class,adjustmentId));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("""
            insert into inventory_adjustments(id,inventory_id,product_id,distributor_id,actor_id,quantity_delta,quantity_after,reason)
            select ?, i.id, i.product_id, ?,
                   (select created_by from distributor_businesses where id=i.distributor_id), 1, i.available_cases + 1, 'Cross-business attempt'
            from inventory i where i.distributor_id='60000000-0000-0000-0000-000000000001' limit 1
            """,UUID.randomUUID(),otherBusiness));
    }
}
