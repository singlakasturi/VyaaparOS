ALTER TABLE orders
    ADD COLUMN fulfillment_status VARCHAR(24) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN tracking_reference VARCHAR(100),
    ADD COLUMN fulfillment_started_at TIMESTAMPTZ,
    ADD COLUMN shipped_at TIMESTAMPTZ,
    ADD COLUMN delivered_at TIMESTAMPTZ;

ALTER TABLE order_status_history
    ADD COLUMN history_type VARCHAR(20) NOT NULL DEFAULT 'ORDER_STATUS',
    ADD COLUMN previous_fulfillment_status VARCHAR(24),
    ADD COLUMN new_fulfillment_status VARCHAR(24),
    ADD COLUMN tracking_reference VARCHAR(100);

ALTER TABLE order_status_history
    ADD CONSTRAINT order_history_type_check CHECK (history_type IN ('ORDER_STATUS', 'FULFILLMENT')),
    ADD CONSTRAINT order_history_fulfillment_state_check CHECK (
        (history_type = 'ORDER_STATUS' AND previous_fulfillment_status IS NULL AND new_fulfillment_status IS NULL)
        OR (history_type = 'FULFILLMENT' AND previous_fulfillment_status IS NOT NULL AND new_fulfillment_status IS NOT NULL)
    );

ALTER TABLE orders
    ADD CONSTRAINT orders_fulfillment_status_check CHECK (
        fulfillment_status IN ('NOT_STARTED', 'PREPARING', 'SHIPPED', 'DELIVERED')
    );
