CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id),
    CONSTRAINT user_roles_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_roles_role_fk
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_role_id
    ON user_roles (role_id);

INSERT INTO roles (id, name, description)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'RETAILER', 'Access for retailer users'),
    ('00000000-0000-0000-0000-000000000002', 'SALESPERSON', 'Access for salesperson users'),
    ('00000000-0000-0000-0000-000000000003', 'ADMIN', 'Access for platform administrators');
