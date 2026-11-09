CREATE TABLE retailer_favorites (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL REFERENCES retailers(id) ON DELETE CASCADE,
    distributor_id UUID NOT NULL REFERENCES distributor_businesses(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    saved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT retailer_favorites_product_distributor_fk
        FOREIGN KEY (product_id, distributor_id)
        REFERENCES products(id, distributor_id) ON DELETE CASCADE,
    CONSTRAINT retailer_favorites_retailer_distributor_product_uq
        UNIQUE (retailer_id, distributor_id, product_id)
);

CREATE INDEX retailer_favorites_retailer_distributor_saved_idx
    ON retailer_favorites(retailer_id, distributor_id, saved_at DESC);
