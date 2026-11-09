CREATE TABLE retailer_relationship_requests (
    id UUID PRIMARY KEY,
    retailer_id UUID NOT NULL REFERENCES retailers(id) ON DELETE RESTRICT,
    distributor_id UUID NOT NULL REFERENCES distributor_businesses(id) ON DELETE RESTRICT,
    requested_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    request_type VARCHAR(16) NOT NULL CHECK (request_type IN ('JOIN', 'SWITCH')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    decided_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT retailer_relationship_request_lifecycle CHECK
      ((status = 'PENDING' AND decided_at IS NULL AND decided_by IS NULL) OR
       (status <> 'PENDING' AND decided_at IS NOT NULL AND decided_by IS NOT NULL))
);
CREATE UNIQUE INDEX retailer_relationship_request_one_pending_idx
    ON retailer_relationship_requests(retailer_id) WHERE status = 'PENDING';
CREATE INDEX retailer_relationship_requests_business_status_idx
    ON retailer_relationship_requests(distributor_id, status, created_at);

CREATE TABLE distributor_retailer_invitations (
    id UUID PRIMARY KEY,
    distributor_id UUID NOT NULL REFERENCES distributor_businesses(id) ON DELETE RESTRICT,
    invited_email VARCHAR(320) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    accepted_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    accepted_retailer_id UUID REFERENCES retailers(id) ON DELETE RESTRICT,
    CONSTRAINT distributor_retailer_invitation_expiry CHECK (expires_at > created_at),
    CONSTRAINT distributor_retailer_invitation_acceptance CHECK
      ((accepted_at IS NULL AND accepted_by IS NULL AND accepted_retailer_id IS NULL) OR
       (accepted_at IS NOT NULL AND accepted_by IS NOT NULL AND accepted_retailer_id IS NOT NULL)),
    CONSTRAINT distributor_retailer_invitation_terminal CHECK
      (revoked_at IS NULL OR accepted_at IS NULL)
);
CREATE INDEX distributor_retailer_invitations_business_created_idx
    ON distributor_retailer_invitations(distributor_id, created_at DESC);
