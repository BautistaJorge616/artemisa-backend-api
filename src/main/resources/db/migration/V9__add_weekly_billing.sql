ALTER TABLE schedule_packages
    ADD COLUMN weekly_price NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_schedule_packages_weekly_price CHECK (weekly_price >= 0);

CREATE TABLE weekly_accounts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    student_id UUID NOT NULL,
    package_id UUID NOT NULL,
    week_start DATE NOT NULL,
    week_end DATE NOT NULL,
    currency CHAR(3) NOT NULL,
    package_code VARCHAR(40) NOT NULL,
    package_version INTEGER NOT NULL,
    package_name VARCHAR(120) NOT NULL,
    weekly_price_snapshot NUMERIC(12,2) NOT NULL,
    contracted_minutes_per_day_snapshot INTEGER NOT NULL,
    overtime_tolerance_minutes_snapshot INTEGER NOT NULL,
    overtime_hourly_rate_snapshot NUMERIC(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    finalized_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_weekly_accounts_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_weekly_accounts_student_week UNIQUE (tenant_id, branch_id, student_id, week_start),
    CONSTRAINT fk_weekly_accounts_student FOREIGN KEY (tenant_id, branch_id, student_id)
        REFERENCES students (tenant_id, branch_id, id),
    CONSTRAINT fk_weekly_accounts_package FOREIGN KEY (tenant_id, branch_id, package_id)
        REFERENCES schedule_packages (tenant_id, branch_id, id),
    CONSTRAINT chk_weekly_accounts_dates CHECK (week_end = week_start + 6),
    CONSTRAINT chk_weekly_accounts_week_start CHECK (EXTRACT(ISODOW FROM week_start) = 1),
    CONSTRAINT chk_weekly_accounts_status CHECK (status IN ('OPEN','FINALIZED'))
);
CREATE INDEX idx_weekly_accounts_branch_week ON weekly_accounts(tenant_id, branch_id, week_start, status);

CREATE TABLE weekly_charges (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    weekly_account_id UUID NOT NULL,
    charge_type VARCHAR(24) NOT NULL,
    service_date DATE,
    description VARCHAR(240) NOT NULL,
    quantity NUMERIC(12,2) NOT NULL DEFAULT 1,
    unit_price NUMERIC(12,2) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_weekly_charges_account FOREIGN KEY (tenant_id, weekly_account_id)
        REFERENCES weekly_accounts (tenant_id, id),
    CONSTRAINT chk_weekly_charges_type CHECK (charge_type IN ('WEEKLY_PACKAGE','OVERTIME','ADJUSTMENT')),
    CONSTRAINT chk_weekly_charges_amounts CHECK (quantity >= 0 AND unit_price >= 0 AND amount >= 0)
);
CREATE INDEX idx_weekly_charges_account ON weekly_charges(tenant_id, weekly_account_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    student_id UUID NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    payment_method VARCHAR(24) NOT NULL,
    reference VARCHAR(120),
    received_by_membership_id UUID NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_payments_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES branches(tenant_id,id),
    CONSTRAINT fk_payments_student FOREIGN KEY (tenant_id, branch_id, student_id)
        REFERENCES students(tenant_id, branch_id, id),
    CONSTRAINT fk_payments_receiver FOREIGN KEY (tenant_id, received_by_membership_id)
        REFERENCES tenant_memberships(tenant_id,id),
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_method CHECK (payment_method IN ('CASH','CARD','BANK_TRANSFER','OTHER')),
    CONSTRAINT chk_payments_status CHECK (status IN ('RECEIVED','VOIDED'))
);
CREATE INDEX idx_payments_student_received ON payments(tenant_id, branch_id, student_id, received_at);

CREATE TABLE payment_allocations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    weekly_account_id UUID NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_allocations_payment_account UNIQUE (tenant_id, payment_id, weekly_account_id),
    CONSTRAINT fk_payment_allocations_payment FOREIGN KEY (tenant_id, payment_id)
        REFERENCES payments(tenant_id,id),
    CONSTRAINT fk_payment_allocations_account FOREIGN KEY (tenant_id, weekly_account_id)
        REFERENCES weekly_accounts(tenant_id,id),
    CONSTRAINT chk_payment_allocations_amount CHECK (amount > 0)
);
CREATE INDEX idx_payment_allocations_account ON payment_allocations(tenant_id, weekly_account_id);
