-- Extend retailer profiles and retailer-scoped membership roles.
-- Ownership transfers must demote the current owner before promoting the new owner
-- within the same transaction because the partial unique index is immediate.

ALTER TABLE retailers
    ADD COLUMN gstin VARCHAR(15),
    ADD COLUMN outstanding_balance NUMERIC(12, 2) NOT NULL DEFAULT 0.00;

ALTER TABLE retailers
    ADD CONSTRAINT retailers_outstanding_balance_check
    CHECK (outstanding_balance >= 0);

ALTER TABLE retailers
    ADD CONSTRAINT retailers_gstin_format_check
    CHECK (
        gstin IS NULL
        OR UPPER(gstin) ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[A-Z0-9]$'
    );

DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a
          ON a.attrelid = c.conrelid
         AND a.attnum = c.conkey[1]
        WHERE c.conrelid = 'retailers'::regclass
          AND c.contype = 'c'
          AND a.attname = 'status'
          AND cardinality(c.conkey) = 1
        ORDER BY c.conname
    LOOP
        EXECUTE format(
            'ALTER TABLE retailers DROP CONSTRAINT %I',
            constraint_record.conname
        );
    END LOOP;
END;
$$;

ALTER TABLE retailers
    ADD CONSTRAINT retailers_status_check
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING', 'SUSPENDED'));

DO $$
DECLARE
    constraint_record RECORD;
BEGIN
    FOR constraint_record IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a
          ON a.attrelid = c.conrelid
         AND a.attnum = c.conkey[1]
        WHERE c.conrelid = 'retailer_memberships'::regclass
          AND c.contype = 'c'
          AND a.attname = 'role'
          AND cardinality(c.conkey) = 1
        ORDER BY c.conname
    LOOP
        EXECUTE format(
            'ALTER TABLE retailer_memberships DROP CONSTRAINT %I',
            constraint_record.conname
        );
    END LOOP;
END;
$$;

ALTER TABLE retailer_memberships
    ADD CONSTRAINT retailer_memberships_role_check
    CHECK (
        role IN (
            'RETAILER_OWNER',
            'RETAILER_MANAGER',
            'RETAILER_STAFF'
        )
    );

CREATE UNIQUE INDEX retailers_gstin_unique_idx
    ON retailers (UPPER(gstin))
    WHERE gstin IS NOT NULL;

CREATE INDEX retailers_status_idx
    ON retailers (status);

CREATE INDEX retailer_memberships_retailer_role_idx
    ON retailer_memberships (retailer_id, role);

CREATE UNIQUE INDEX retailer_memberships_one_owner_idx
    ON retailer_memberships (retailer_id)
    WHERE role = 'RETAILER_OWNER';
