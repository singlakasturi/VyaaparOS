CREATE SEQUENCE order_number_seq START WITH 100001 INCREMENT BY 1;

CREATE TABLE carts (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL REFERENCES retailers(id),
    created_by UUID NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT carts_status_check CHECK (status IN ('ACTIVE', 'CHECKED_OUT'))
);
CREATE UNIQUE INDEX carts_active_creator_retailer_idx
    ON carts (retailer_id, created_by) WHERE status = 'ACTIVE';

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    paid_cases INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cart_items_quantity_check CHECK (paid_cases > 0),
    CONSTRAINT cart_items_cart_product_unique UNIQUE (cart_id, product_id)
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(30) NOT NULL UNIQUE,
    retailer_id UUID NOT NULL REFERENCES retailers(id),
    created_by UUID NOT NULL REFERENCES users(id),
    client_order_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    base_amount NUMERIC(14,2) NOT NULL,
    discount_amount NUMERIC(14,2) NOT NULL,
    final_amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    credit_hold_reason VARCHAR(500),
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT orders_retailer_client_unique UNIQUE (retailer_id, client_order_id),
    CONSTRAINT orders_status_check CHECK (status IN ('SUBMITTED', 'CREDIT_HOLD')),
    CONSTRAINT orders_amounts_check CHECK (
        base_amount >= 0 AND discount_amount >= 0 AND final_amount >= 0
    ),
    CONSTRAINT orders_currency_check CHECK (currency = 'INR')
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    product_sku VARCHAR(60) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    paid_cases INTEGER NOT NULL,
    free_cases INTEGER NOT NULL,
    total_cases INTEGER NOT NULL,
    unit_price_per_case NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    gst_included BOOLEAN,
    base_amount NUMERIC(14,2) NOT NULL,
    discount_amount NUMERIC(14,2) NOT NULL,
    final_amount NUMERIC(14,2) NOT NULL,
    applied_scheme_id UUID,
    scheme_name VARCHAR(150),
    scheme_type VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT order_items_quantity_check CHECK (
        paid_cases > 0 AND free_cases >= 0 AND total_cases = paid_cases + free_cases
    ),
    CONSTRAINT order_items_amounts_check CHECK (
        unit_price_per_case >= 0 AND base_amount >= 0
        AND discount_amount >= 0 AND final_amount >= 0
    ),
    CONSTRAINT order_items_currency_check CHECK (currency = 'INR')
);

CREATE INDEX carts_creator_idx ON carts (created_by);
CREATE UNIQUE INDEX cart_items_product_idx ON cart_items (product_id, cart_id);
CREATE INDEX orders_retailer_created_idx ON orders (retailer_id, created_at DESC);
CREATE INDEX orders_status_idx ON orders (status);
CREATE INDEX orders_created_idx ON orders (created_at DESC);
CREATE INDEX order_items_product_idx ON order_items (product_id);
