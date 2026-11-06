-- Allow minimal retailer onboarding without inventing profile values.
ALTER TABLE retailers
    ALTER COLUMN owner_name DROP NOT NULL,
    ALTER COLUMN city DROP NOT NULL,
    ALTER COLUMN state DROP NOT NULL,
    ALTER COLUMN pincode DROP NOT NULL;
