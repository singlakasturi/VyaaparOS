CREATE TABLE product_schemes (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    retailer_id UUID,
    scheme_type VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    buy_quantity INTEGER,
    free_quantity INTEGER,
    minimum_quantity INTEGER,
    discount_percentage NUMERIC(5, 2),
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT product_schemes_product_fk FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT product_schemes_retailer_fk FOREIGN KEY (retailer_id) REFERENCES retailers (id),
    CONSTRAINT product_schemes_type_check CHECK (
        scheme_type IN ('BUY_X_GET_Y_FREE', 'SLAB_DISCOUNT')
    ),
    CONSTRAINT product_schemes_fields_check CHECK (
        (scheme_type = 'BUY_X_GET_Y_FREE'
            AND buy_quantity > 0 AND free_quantity > 0
            AND minimum_quantity IS NULL AND discount_percentage IS NULL)
        OR
        (scheme_type = 'SLAB_DISCOUNT'
            AND minimum_quantity > 0 AND discount_percentage > 0
            AND discount_percentage <= 100
            AND buy_quantity IS NULL AND free_quantity IS NULL)
    ),
    CONSTRAINT product_schemes_dates_check CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at),
    CONSTRAINT product_schemes_quantities_check CHECK (
        (buy_quantity IS NULL OR buy_quantity > 0)
        AND (free_quantity IS NULL OR free_quantity > 0)
        AND (minimum_quantity IS NULL OR minimum_quantity > 0)
        AND (discount_percentage IS NULL OR discount_percentage > 0)
    )
);

CREATE INDEX product_schemes_product_idx ON product_schemes (product_id);
CREATE INDEX product_schemes_retailer_idx ON product_schemes (retailer_id);
CREATE INDEX product_schemes_active_validity_idx
    ON product_schemes (active, starts_at, ends_at);
CREATE INDEX product_schemes_product_retailer_active_idx
    ON product_schemes (product_id, retailer_id, active);

INSERT INTO product_schemes (
    id, product_id, scheme_type, name, buy_quantity, free_quantity
)
SELECT
    seed.id, product.id, seed.scheme_type, seed.name, seed.buy_quantity, seed.free_quantity
FROM (
    VALUES (
        '50000000-0000-0000-0000-000000000001'::uuid,
        'SKU-BS-075', 'BUY_X_GET_Y_FREE', 'Buy 10 cases, get 1 free', 10, 1
    )
) AS seed(id, sku, scheme_type, name, buy_quantity, free_quantity)
JOIN products product ON product.sku = seed.sku
ON CONFLICT (id) DO NOTHING;

INSERT INTO product_schemes (
    id, product_id, scheme_type, name, minimum_quantity, discount_percentage
)
SELECT
    seed.id, product.id, seed.scheme_type, seed.name, seed.minimum_quantity, seed.discount_percentage
FROM (
    VALUES (
        '50000000-0000-0000-0000-000000000002'::uuid,
        'SKU-DT-1KG', 'SLAB_DISCOUNT', '3% off on 5 or more cases', 5, 3.00::numeric
    )
) AS seed(id, sku, scheme_type, name, minimum_quantity, discount_percentage)
JOIN products product ON product.sku = seed.sku
ON CONFLICT (id) DO NOTHING;
