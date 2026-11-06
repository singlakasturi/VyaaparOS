ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('SUBMITTED', 'CREDIT_HOLD', 'APPROVED', 'REJECTED', 'TALLY_EXPORTED'));

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_by UUID NOT NULL REFERENCES users(id),
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX order_status_history_order_created_idx
    ON order_status_history (order_id, created_at DESC);

CREATE TABLE tally_export_batches (
    id UUID PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    order_count INTEGER NOT NULL CHECK (order_count >= 0)
);

CREATE TABLE tally_export_batch_orders (
    batch_id UUID NOT NULL REFERENCES tally_export_batches(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    PRIMARY KEY (batch_id, order_id),
    CONSTRAINT tally_export_order_once UNIQUE (order_id)
);
