CREATE TABLE platform_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_platform_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
CREATE UNIQUE INDEX uq_platform_users_email_lower ON platform_users (lower(email));

CREATE TABLE platform_role_assignments (
    platform_user_id UUID NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    role_code VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (platform_user_id, role_code),
    CONSTRAINT ck_platform_role_code CHECK (role_code IN ('PLATFORM_ADMIN'))
);

ALTER TABLE tenants ADD COLUMN created_by_platform_user_id UUID;
ALTER TABLE tenants ADD CONSTRAINT fk_tenants_created_by_platform_user
    FOREIGN KEY (created_by_platform_user_id) REFERENCES platform_users(id);
CREATE INDEX ix_tenants_created_by_platform_user ON tenants(created_by_platform_user_id);
