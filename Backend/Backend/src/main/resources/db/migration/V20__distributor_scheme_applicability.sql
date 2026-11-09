-- Add category-specific and distributor-wide scheme targets while retaining existing product targets.
ALTER TABLE product_schemes ADD COLUMN category_id UUID;

-- Existing product schemes continue targeting their product; new schemes may target
-- one category or the whole distributor. Historical rows are preserved as-is.
ALTER TABLE product_schemes ALTER COLUMN product_id DROP NOT NULL;
ALTER TABLE product_schemes ADD CONSTRAINT product_schemes_applicability_check
    CHECK (num_nonnulls(product_id, category_id) <= 1);
ALTER TABLE product_schemes ADD CONSTRAINT product_schemes_category_distributor_fk
    FOREIGN KEY (category_id, distributor_id)
    REFERENCES product_categories(id, distributor_id) ON DELETE RESTRICT;
CREATE INDEX product_schemes_category_active_idx ON product_schemes(category_id, active);
CREATE INDEX product_schemes_distributor_active_idx ON product_schemes(distributor_id, active, starts_at, ends_at);
