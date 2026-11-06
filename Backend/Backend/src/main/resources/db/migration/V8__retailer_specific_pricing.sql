CREATE TABLE product_prices (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    retailer_id UUID,
    price_per_case NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    gst_included BOOLEAN,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT product_prices_product_fk
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT product_prices_retailer_fk
        FOREIGN KEY (retailer_id) REFERENCES retailers (id),
    CONSTRAINT product_prices_amount_check CHECK (price_per_case >= 0),
    CONSTRAINT product_prices_currency_check CHECK (currency = 'INR')
);

CREATE UNIQUE INDEX product_prices_active_default_idx
    ON product_prices (product_id)
    WHERE retailer_id IS NULL AND active = TRUE;

CREATE UNIQUE INDEX product_prices_active_retailer_idx
    ON product_prices (retailer_id, product_id)
    WHERE retailer_id IS NOT NULL AND active = TRUE;

CREATE INDEX product_prices_product_idx ON product_prices (product_id);
CREATE INDEX product_prices_retailer_idx ON product_prices (retailer_id);

-- GST inclusion was not supplied for these source prices; NULL means unknown.
INSERT INTO product_prices (id, product_id, price_per_case, gst_included)
SELECT
    seed.id,
    product.id,
    seed.price_per_case,
    NULL
FROM (
    VALUES
        (
            '40000000-0000-0000-0000-000000000001'::uuid,
            'SKU-BS-075',
            8.10::numeric
        ),
        (
            '40000000-0000-0000-0000-000000000002'::uuid,
            'SKU-DT-1KG',
            101.50::numeric
        ),
        (
            '40000000-0000-0000-0000-000000000003'::uuid,
            'SKU-TE-250',
            118.00::numeric
        )
) AS seed(id, sku, price_per_case)
JOIN products product ON product.sku = seed.sku
ON CONFLICT (id) DO NOTHING;
