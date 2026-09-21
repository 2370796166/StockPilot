CREATE INDEX idx_inventory_balance_warehouse_id
    ON inventory_balance (warehouse_id, id);
