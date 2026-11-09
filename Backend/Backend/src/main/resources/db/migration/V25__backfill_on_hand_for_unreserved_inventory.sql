-- Ensure products with no outstanding reservations still get their current
-- available balance copied to on-hand stock during the inventory-model upgrade.
UPDATE inventory
SET on_hand_cases = available_cases + reserved_cases;
