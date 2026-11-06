CREATE TABLE salesperson_retailer_assignments (
    id UUID PRIMARY KEY,
    salesperson_id UUID NOT NULL,
    retailer_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT salesperson_retailer_assignments_salesperson_fk
        FOREIGN KEY (salesperson_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT salesperson_retailer_assignments_retailer_fk
        FOREIGN KEY (retailer_id) REFERENCES retailers (id) ON DELETE CASCADE,
    CONSTRAINT salesperson_retailer_assignments_pair_unique
        UNIQUE (salesperson_id, retailer_id)
);

CREATE INDEX salesperson_retailer_assignments_salesperson_idx
    ON salesperson_retailer_assignments (salesperson_id);

CREATE INDEX salesperson_retailer_assignments_retailer_idx
    ON salesperson_retailer_assignments (retailer_id);

INSERT INTO roles (id, name, description)
VALUES (
    '00000000-0000-0000-0000-000000000004',
    'ORDER_DESK',
    'Access for order desk users'
)
ON CONFLICT (name) DO NOTHING;
