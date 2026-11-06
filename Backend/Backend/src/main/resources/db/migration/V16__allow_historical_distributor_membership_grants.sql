-- Keep grant/revocation history while ensuring a user has at most one current
-- Order Desk membership for a given distributor.
ALTER TABLE distributor_business_memberships
    DROP CONSTRAINT distributor_business_memberships_pair_unique;
CREATE UNIQUE INDEX distributor_business_memberships_one_active_idx
    ON distributor_business_memberships(distributor_id, user_id)
    WHERE status = 'ACTIVE';
CREATE INDEX distributor_business_memberships_history_idx
    ON distributor_business_memberships(distributor_id, user_id, granted_at DESC);
