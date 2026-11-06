-- ADMIN and SALESPERSON are no longer application roles. V14 removed their
-- user grants before this migration; historical business ownership and
-- distributor memberships are stored independently and are not promoted.
DELETE FROM roles WHERE name IN ('ADMIN', 'SALESPERSON');
