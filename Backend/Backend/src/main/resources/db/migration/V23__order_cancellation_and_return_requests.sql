ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('SUBMITTED', 'CREDIT_HOLD', 'APPROVED', 'REJECTED', 'TALLY_EXPORTED', 'CANCELLED'));

CREATE TABLE order_action_requests (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    request_type VARCHAR(20) NOT NULL CHECK (request_type IN ('CANCELLATION', 'RETURN')),
    request_status VARCHAR(16) NOT NULL CHECK (request_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_by UUID NOT NULL REFERENCES users(id),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    decided_by UUID REFERENCES users(id),
    decided_at TIMESTAMPTZ,
    decision_comment VARCHAR(500),
    CONSTRAINT order_action_request_reason_check CHECK (
        request_type <> 'RETURN' OR (reason IS NOT NULL AND length(trim(reason)) > 0)
    ),
    CONSTRAINT order_action_request_decision_check CHECK (
        (request_status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
        OR (request_status IN ('APPROVED', 'REJECTED') AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
    )
);

CREATE INDEX order_action_request_order_created_idx ON order_action_requests(order_id, created_at DESC);
CREATE INDEX order_action_request_pending_idx ON order_action_requests(request_status, created_at DESC) WHERE request_status = 'PENDING';
CREATE UNIQUE INDEX order_action_request_one_pending_idx ON order_action_requests(order_id, request_type) WHERE request_status = 'PENDING';

ALTER TABLE order_status_history
    ALTER COLUMN history_type TYPE VARCHAR(24),
    ADD COLUMN request_id UUID REFERENCES order_action_requests(id) ON DELETE RESTRICT,
    ADD COLUMN action_request_status VARCHAR(16);

ALTER TABLE order_status_history DROP CONSTRAINT order_history_type_check;
ALTER TABLE order_status_history DROP CONSTRAINT order_history_fulfillment_state_check;

ALTER TABLE order_status_history
    ADD CONSTRAINT order_history_type_check CHECK (history_type IN (
        'ORDER_STATUS', 'FULFILLMENT', 'CANCELLATION_REQUEST', 'CANCELLATION_DECISION', 'RETURN_REQUEST', 'RETURN_DECISION'
    )),
    ADD CONSTRAINT order_history_event_fields_check CHECK (
        (history_type = 'ORDER_STATUS' AND previous_fulfillment_status IS NULL AND new_fulfillment_status IS NULL AND request_id IS NULL AND action_request_status IS NULL)
        OR (history_type = 'FULFILLMENT' AND previous_fulfillment_status IS NOT NULL AND new_fulfillment_status IS NOT NULL AND request_id IS NULL AND action_request_status IS NULL)
        OR (history_type IN ('CANCELLATION_REQUEST', 'CANCELLATION_DECISION', 'RETURN_REQUEST', 'RETURN_DECISION')
            AND previous_fulfillment_status IS NULL AND new_fulfillment_status IS NULL
            AND request_id IS NOT NULL AND action_request_status IN ('PENDING', 'APPROVED', 'REJECTED'))
    );
