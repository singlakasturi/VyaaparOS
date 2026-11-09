ALTER TABLE inventory
    ADD COLUMN reserved_cases INTEGER NOT NULL DEFAULT 0 CHECK (reserved_cases >= 0),
    ADD COLUMN on_hand_cases INTEGER NOT NULL DEFAULT 0 CHECK (on_hand_cases >= 0),
    ADD COLUMN low_stock_threshold INTEGER NOT NULL DEFAULT 0 CHECK (low_stock_threshold >= 0);

-- Existing available_cases already excludes quantities reserved by outstanding orders.
-- Reconstruct reserved and on-hand balances from those persisted order snapshots.
UPDATE inventory i
SET reserved_cases = COALESCE(r.reserved, 0),
    on_hand_cases = i.available_cases + COALESCE(r.reserved, 0)
FROM (
    SELECT oi.product_id, o.distributor_id, SUM(oi.total_cases)::INTEGER AS reserved
    FROM order_items oi
    JOIN orders o ON o.id = oi.order_id
    WHERE o.status NOT IN ('REJECTED', 'CANCELLED')
      AND o.fulfillment_status <> 'DELIVERED'
    GROUP BY oi.product_id, o.distributor_id
) r
WHERE i.product_id = r.product_id AND i.distributor_id = r.distributor_id;

ALTER TABLE inventory
    ADD CONSTRAINT inventory_stock_balance_check CHECK (on_hand_cases = available_cases + reserved_cases);
