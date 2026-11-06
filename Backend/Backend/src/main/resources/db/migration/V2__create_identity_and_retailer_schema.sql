CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    full_name VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'PENDING', 'SUSPENDED'))
);

CREATE INDEX idx_users_email_lower ON users (LOWER(email));

CREATE TABLE retailers (
    id UUID PRIMARY KEY,
    business_name VARCHAR(200) NOT NULL,
    owner_name VARCHAR(200) NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    address VARCHAR(500) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    pincode VARCHAR(20) NOT NULL,
    credit_limit NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT retailers_credit_limit_check CHECK (credit_limit >= 0),
    CONSTRAINT retailers_status_check CHECK (status IN ('ACTIVE', 'PENDING', 'SUSPENDED'))
);

CREATE INDEX idx_retailers_contact_email_lower
    ON retailers (LOWER(contact_email));

CREATE TABLE retailer_memberships (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    retailer_id UUID NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT retailer_memberships_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT retailer_memberships_retailer_fk
        FOREIGN KEY (retailer_id) REFERENCES retailers (id) ON DELETE CASCADE,
    CONSTRAINT retailer_memberships_role_check
        CHECK (role IN ('RETAILER_OWNER', 'RETAILER_STAFF')),
    CONSTRAINT retailer_memberships_user_retailer_unique
        UNIQUE (user_id, retailer_id)
);

CREATE INDEX idx_retailer_memberships_user_id
    ON retailer_memberships (user_id);

CREATE INDEX idx_retailer_memberships_retailer_id
    ON retailer_memberships (retailer_id);

CREATE TABLE otp_challenges (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT otp_challenges_attempts_check CHECK (attempts >= 0),
    CONSTRAINT otp_challenges_expiry_check CHECK (expires_at > created_at)
);

CREATE INDEX idx_otp_challenges_email
    ON otp_challenges (LOWER(email));

CREATE INDEX idx_otp_challenges_active_email
    ON otp_challenges (LOWER(email), expires_at)
    WHERE consumed_at IS NULL;
