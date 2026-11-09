-- Replace the legacy global identifiers with distributor-scoped uniqueness.
ALTER TABLE products DROP CONSTRAINT products_sku_key;
CREATE UNIQUE INDEX products_distributor_sku_uq
    ON products(distributor_id, upper(sku));

ALTER TABLE product_categories DROP CONSTRAINT product_categories_slug_key;
CREATE UNIQUE INDEX product_categories_distributor_slug_uq
    ON product_categories(distributor_id, lower(slug));

-- Inventory adjustments are append-only and preserve the existing available_cases
-- semantics used by order placement and restoration.
ALTER TABLE inventory
    ADD CONSTRAINT inventory_id_distributor_unique UNIQUE (id, distributor_id);

CREATE TABLE inventory_adjustments (
    id UUID PRIMARY KEY,
    inventory_id UUID NOT NULL,
    product_id UUID NOT NULL,
    distributor_id UUID NOT NULL,
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    quantity_delta INTEGER NOT NULL CHECK (quantity_delta <> 0),
    quantity_after INTEGER NOT NULL CHECK (quantity_after >= 0),
    reason VARCHAR(500) NOT NULL CHECK (length(btrim(reason)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_adjustments_inventory_business_fk
        FOREIGN KEY (inventory_id, distributor_id)
        REFERENCES inventory(id, distributor_id) ON DELETE RESTRICT,
    CONSTRAINT inventory_adjustments_product_business_fk
        FOREIGN KEY (product_id, distributor_id)
        REFERENCES products(id, distributor_id) ON DELETE RESTRICT
);
CREATE INDEX inventory_adjustments_history_idx
    ON inventory_adjustments(inventory_id, created_at DESC, id DESC);
