ALTER TABLE cart_items ADD COLUMN position INTEGER;
WITH ordered_cart_items AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY cart_id ORDER BY created_at, id) - 1 AS item_position
    FROM cart_items
)
UPDATE cart_items item
SET position = ordered_cart_items.item_position
FROM ordered_cart_items
WHERE item.id = ordered_cart_items.id;
ALTER TABLE cart_items ALTER COLUMN position SET NOT NULL;
CREATE UNIQUE INDEX cart_items_cart_position_unique
    ON cart_items (cart_id, position);

ALTER TABLE order_items ADD COLUMN position INTEGER;
WITH ordered_order_items AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY created_at, id) - 1 AS item_position
    FROM order_items
)
UPDATE order_items item
SET position = ordered_order_items.item_position
FROM ordered_order_items
WHERE item.id = ordered_order_items.id;
ALTER TABLE order_items ALTER COLUMN position SET NOT NULL;
CREATE UNIQUE INDEX order_items_order_position_unique
    ON order_items (order_id, position);
