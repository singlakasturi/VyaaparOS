CREATE TABLE product_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    brand VARCHAR(120),
    category_id UUID NOT NULL,
    unit_name VARCHAR(50) NOT NULL,
    unit_quantity NUMERIC(12, 3) NOT NULL,
    units_per_case INTEGER NOT NULL,
    case_description VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT products_category_fk
        FOREIGN KEY (category_id) REFERENCES product_categories (id),
    CONSTRAINT products_unit_quantity_check CHECK (unit_quantity > 0),
    CONSTRAINT products_units_per_case_check CHECK (units_per_case > 0)
);

CREATE TABLE inventory (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE,
    available_cases INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_product_fk
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT inventory_available_cases_check CHECK (available_cases >= 0)
);

CREATE INDEX products_category_idx ON products (category_id);
CREATE INDEX products_active_idx ON products (active);
CREATE INDEX inventory_product_idx ON inventory (product_id);

INSERT INTO product_categories (id, name, slug)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'Biscuits', 'biscuits'),
    ('10000000-0000-0000-0000-000000000002', 'Detergents', 'detergents'),
    ('10000000-0000-0000-0000-000000000003', 'Tea', 'tea')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO products (
    id, sku, name, category_id, unit_name, unit_quantity,
    units_per_case, case_description
)
SELECT
    '20000000-0000-0000-0000-000000000001',
    'SKU-BS-075',
    'Glucose biscuit 75 g',
    id,
    'g',
    75,
    96,
    '96 packs per case'
FROM product_categories
WHERE slug = 'biscuits'
ON CONFLICT (sku) DO NOTHING;

INSERT INTO products (
    id, sku, name, category_id, unit_name, unit_quantity,
    units_per_case, case_description
)
SELECT
    '20000000-0000-0000-0000-000000000002',
    'SKU-DT-1KG',
    'Detergent powder 1 kg',
    id,
    'kg',
    1,
    20,
    '20 packs per case'
FROM product_categories
WHERE slug = 'detergents'
ON CONFLICT (sku) DO NOTHING;

INSERT INTO products (
    id, sku, name, category_id, unit_name, unit_quantity,
    units_per_case, case_description
)
SELECT
    '20000000-0000-0000-0000-000000000003',
    'SKU-TE-250',
    'Tea leaf 250 g',
    id,
    'g',
    250,
    40,
    '40 packs per case'
FROM product_categories
WHERE slug = 'tea'
ON CONFLICT (sku) DO NOTHING;

INSERT INTO inventory (id, product_id, available_cases)
VALUES
    (
        '30000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        340
    ),
    (
        '30000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000002',
        85
    ),
    (
        '30000000-0000-0000-0000-000000000003',
        '20000000-0000-0000-0000-000000000003',
        0
    )
ON CONFLICT (product_id) DO NOTHING;
