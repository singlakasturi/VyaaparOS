-- Ensure distributor-scoped catalogue rows cannot point across business bounds.
ALTER TABLE product_categories
    ADD CONSTRAINT product_categories_id_distributor_unique UNIQUE (id, distributor_id);
ALTER TABLE products
    ADD CONSTRAINT products_id_distributor_unique UNIQUE (id, distributor_id),
    ADD CONSTRAINT products_category_distributor_fk
        FOREIGN KEY (category_id, distributor_id)
        REFERENCES product_categories(id, distributor_id) ON DELETE RESTRICT;
ALTER TABLE inventory
    ADD CONSTRAINT inventory_product_distributor_fk
        FOREIGN KEY (product_id, distributor_id)
        REFERENCES products(id, distributor_id) ON DELETE RESTRICT;
ALTER TABLE product_prices
    ADD CONSTRAINT product_prices_product_distributor_fk
        FOREIGN KEY (product_id, distributor_id)
        REFERENCES products(id, distributor_id) ON DELETE RESTRICT;
ALTER TABLE product_schemes
    ADD CONSTRAINT product_schemes_product_distributor_fk
        FOREIGN KEY (product_id, distributor_id)
        REFERENCES products(id, distributor_id) ON DELETE RESTRICT;
