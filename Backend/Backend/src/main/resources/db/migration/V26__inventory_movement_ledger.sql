CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    inventory_id UUID NOT NULL,
    product_id UUID NOT NULL,
    distributor_id UUID NOT NULL,
    order_id UUID REFERENCES orders(id) ON DELETE RESTRICT,
    actor_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    movement_type VARCHAR(32) NOT NULL CHECK (movement_type IN (
        'OPENING_BALANCE', 'MANUAL_ADJUSTMENT', 'ORDER_RESERVATION',
        'RESERVATION_RELEASE', 'DELIVERY_CONSUMPTION'
    )),
    on_hand_delta INTEGER NOT NULL DEFAULT 0,
    reserved_delta INTEGER NOT NULL DEFAULT 0,
    on_hand_after INTEGER CHECK (on_hand_after >= 0),
    reserved_after INTEGER CHECK (reserved_after >= 0),
    available_after INTEGER CHECK (available_after >= 0),
    event_key VARCHAR(200) NOT NULL,
    reference VARCHAR(200),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_movements_event_key_uq UNIQUE (event_key),
    CONSTRAINT inventory_movements_event_delta_check CHECK (
        (movement_type = 'OPENING_BALANCE' AND on_hand_delta = 0 AND reserved_delta = 0)
        OR (movement_type <> 'OPENING_BALANCE' AND (on_hand_delta <> 0 OR reserved_delta <> 0))
    ),
    CONSTRAINT inventory_movements_inventory_business_fk FOREIGN KEY (inventory_id, distributor_id)
        REFERENCES inventory(id, distributor_id) ON DELETE RESTRICT,
    CONSTRAINT inventory_movements_product_business_fk FOREIGN KEY (product_id, distributor_id)
        REFERENCES products(id, distributor_id) ON DELETE RESTRICT
);

CREATE INDEX inventory_movements_distributor_history_idx
    ON inventory_movements(distributor_id, created_at DESC, id DESC);
CREATE INDEX inventory_movements_product_history_idx
    ON inventory_movements(distributor_id, product_id, created_at DESC, id DESC);
CREATE INDEX inventory_movements_order_idx
    ON inventory_movements(order_id, created_at DESC) WHERE order_id IS NOT NULL;

-- Existing manual adjustments carry reliable deltas, actors, reasons, timestamps,
-- and available-after values. Preserve those exact facts without inventing other balances.
INSERT INTO inventory_movements (
    id, inventory_id, product_id, distributor_id, actor_id, movement_type,
    on_hand_delta, reserved_delta, available_after, event_key, reference, reason, created_at
)
SELECT gen_random_uuid(), a.inventory_id, a.product_id, a.distributor_id, a.actor_id,
    'MANUAL_ADJUSTMENT', a.quantity_delta, 0, a.quantity_after,
    'LEGACY_ADJUSTMENT:' || a.id::text, 'Adjustment ' || a.id::text, a.reason, a.created_at
FROM inventory_adjustments a;

-- This is a real opening snapshot at migration time, not a reconstruction of past movements.
INSERT INTO inventory_movements (
    id, inventory_id, product_id, distributor_id, movement_type,
    on_hand_delta, reserved_delta, on_hand_after, reserved_after, available_after,
    event_key, reference, reason, created_at
)
SELECT gen_random_uuid(), i.id, i.product_id, i.distributor_id, 'OPENING_BALANCE',
    0, 0, i.on_hand_cases, i.reserved_cases, i.available_cases,
    'BASELINE:' || i.id::text, NULL, 'Opening balance at movement-ledger activation', CURRENT_TIMESTAMP
FROM inventory i;
