-- DEVELOPMENT/TEST DATA ONLY.
-- The current product/inventory/pricing/scheme data is unscoped seed data and
-- all rows in this database are disposable development records. This migration
-- assigns that data to one seeded development business. Do NOT use this backfill
-- strategy for production: production ownership must be established from an
-- authoritative source before adding business scope.

CREATE TABLE distributor_businesses (
    id UUID PRIMARY KEY,
    business_name VARCHAR(200) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT distributor_businesses_creator_fk
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE TABLE distributor_business_memberships (
    id UUID PRIMARY KEY,
    distributor_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT distributor_business_memberships_business_fk
        FOREIGN KEY (distributor_id) REFERENCES distributor_businesses(id) ON DELETE RESTRICT,
    CONSTRAINT distributor_business_memberships_user_fk
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT distributor_business_memberships_status_check
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT distributor_business_memberships_lifecycle_check
        CHECK ((status = 'ACTIVE' AND revoked_at IS NULL) OR (status = 'REVOKED' AND revoked_at IS NOT NULL)),
    CONSTRAINT distributor_business_memberships_pair_unique UNIQUE (distributor_id, user_id)
);
CREATE INDEX distributor_business_memberships_user_status_idx
    ON distributor_business_memberships(user_id, status);

CREATE TABLE retailer_distributor_relationships (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL,
    distributor_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_by UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMPTZ,
    CONSTRAINT retailer_distributor_relationships_retailer_fk
        FOREIGN KEY (retailer_id) REFERENCES retailers(id) ON DELETE RESTRICT,
    CONSTRAINT retailer_distributor_relationships_distributor_fk
        FOREIGN KEY (distributor_id) REFERENCES distributor_businesses(id) ON DELETE RESTRICT,
    CONSTRAINT retailer_distributor_relationships_started_by_fk
        FOREIGN KEY (started_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT retailer_distributor_relationships_status_check
        CHECK (status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT retailer_distributor_relationships_lifecycle_check
        CHECK ((status = 'ACTIVE' AND ended_at IS NULL) OR (status = 'ENDED' AND ended_at IS NOT NULL))
);
CREATE UNIQUE INDEX retailer_distributor_relationship_one_active_idx
    ON retailer_distributor_relationships(retailer_id) WHERE status = 'ACTIVE';
CREATE INDEX retailer_distributor_relationship_history_idx
    ON retailer_distributor_relationships(retailer_id, started_at DESC);

-- The configured DevelopmentUserSeeder identity is the sole known development
-- distributor creator. Fail closed if that identity is absent or ambiguous.
DO $$
DECLARE
    creator_id UUID;
    business_id UUID := '60000000-0000-0000-0000-000000000001';
BEGIN
    SELECT id INTO creator_id FROM users
    WHERE LOWER(email) = 'kasturisingla0@gmail.com' AND status = 'ACTIVE';
    IF creator_id IS NULL THEN
        RAISE EXCEPTION 'Development distributor backfill requires active configured development user kasturisingla0@gmail.com';
    END IF;

    INSERT INTO distributor_businesses(id, business_name, created_by)
    VALUES (business_id, 'VyapaarOS Development Distributor', creator_id);
    INSERT INTO distributor_business_memberships(id, distributor_id, user_id, status)
    VALUES ('60000000-0000-0000-0000-000000000002', business_id, creator_id, 'ACTIVE');

    INSERT INTO retailer_distributor_relationships
        (id, retailer_id, distributor_id, status, started_by, started_at)
    SELECT gen_random_uuid(), r.id, business_id, 'ACTIVE',
           COALESCE(owner_membership.user_id, creator_id), r.created_at
    FROM retailers r
    LEFT JOIN LATERAL (
        SELECT rm.user_id FROM retailer_memberships rm
        WHERE rm.retailer_id = r.id AND rm.role = 'RETAILER_OWNER'
        ORDER BY rm.created_at, rm.id LIMIT 1
    ) owner_membership ON TRUE;
END $$;

ALTER TABLE product_categories ADD COLUMN distributor_id UUID;
ALTER TABLE products ADD COLUMN distributor_id UUID;
ALTER TABLE inventory ADD COLUMN distributor_id UUID;
ALTER TABLE product_prices ADD COLUMN distributor_id UUID;
ALTER TABLE product_schemes ADD COLUMN distributor_id UUID;
ALTER TABLE orders ADD COLUMN distributor_id UUID;
ALTER TABLE tally_export_batches ADD COLUMN distributor_id UUID;

UPDATE product_categories SET distributor_id = '60000000-0000-0000-0000-000000000001';
UPDATE products SET distributor_id = '60000000-0000-0000-0000-000000000001';
UPDATE inventory SET distributor_id = '60000000-0000-0000-0000-000000000001';
UPDATE product_prices SET distributor_id = '60000000-0000-0000-0000-000000000001';
UPDATE product_schemes SET distributor_id = '60000000-0000-0000-0000-000000000001';
UPDATE orders o SET distributor_id = r.distributor_id
FROM retailer_distributor_relationships r
WHERE r.retailer_id = o.retailer_id AND r.status = 'ACTIVE';
UPDATE tally_export_batches SET distributor_id = '60000000-0000-0000-0000-000000000001';

ALTER TABLE product_categories ALTER COLUMN distributor_id SET NOT NULL;
ALTER TABLE products ALTER COLUMN distributor_id SET NOT NULL;
ALTER TABLE inventory ALTER COLUMN distributor_id SET NOT NULL;
ALTER TABLE product_prices ALTER COLUMN distributor_id SET NOT NULL;
ALTER TABLE product_schemes ALTER COLUMN distributor_id SET NOT NULL;
ALTER TABLE orders ALTER COLUMN distributor_id SET NOT NULL;
ALTER TABLE tally_export_batches ALTER COLUMN distributor_id SET NOT NULL;

ALTER TABLE product_categories ADD CONSTRAINT product_categories_distributor_fk
    FOREIGN KEY (distributor_id) REFERENCES distributor_businesses(id) ON DELETE RESTRICT;
ALTER TABLE products ADD CONSTRAINT products_distributor_fk
    FOREIGN KEY (distributor_id) REFERENCES distributor_businesses(id) ON DELETE RESTRICT;
ALTER TABLE inventory ADD CONSTRAINT inventory_distributor_fk
    FOREIGN KEY (distributor_id) REFERENCES distributor_businesses(id) ON DELETE RESTRICT;
ALTER TABLE product_prices ADD CONSTRAINT product_prices_distributor_fk
    FOREIGN KEY (distributor_id) REFERENCES distributor_businesses(id) ON DELETE RESTRICT;
ALTER TABLE product_schemes ADD CONSTRAINT product_schemes_distributor_fk
    FOREIGN KEY (distributor_id) REFERENCES distributor_businesses(id) ON DELETE RESTRICT;
ALTER TABLE orders ADD CONSTRAINT orders_distributor_fk
    FOREIGN KEY (distributor_id) REFERENCES distributor_businesses(id) ON DELETE RESTRICT;
ALTER TABLE tally_export_batches ADD CONSTRAINT tally_export_batches_distributor_fk
    FOREIGN KEY (distributor_id) REFERENCES distributor_businesses(id) ON DELETE RESTRICT;

CREATE INDEX product_categories_distributor_idx ON product_categories(distributor_id);
CREATE INDEX products_distributor_idx ON products(distributor_id);
CREATE INDEX inventory_distributor_idx ON inventory(distributor_id);
CREATE INDEX product_prices_distributor_idx ON product_prices(distributor_id);
CREATE INDEX product_schemes_distributor_idx ON product_schemes(distributor_id);
CREATE INDEX orders_distributor_created_idx ON orders(distributor_id, created_at DESC);
CREATE INDEX tally_export_batches_distributor_created_idx ON tally_export_batches(distributor_id, created_at DESC);

-- Keep legacy role definitions for referential compatibility, but remove their
-- global grants. In particular, do not convert legacy ADMIN/ORDER_DESK users
-- into business members. Business access is represented only by the scoped
-- membership table; SALESPERSON and ADMIN are no longer application roles.
DELETE FROM user_roles ur
USING roles r
WHERE ur.role_id = r.id AND r.name IN ('ADMIN', 'SALESPERSON', 'ORDER_DESK');
