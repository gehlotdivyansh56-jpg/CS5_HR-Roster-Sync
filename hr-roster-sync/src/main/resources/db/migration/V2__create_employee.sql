-- V2__create_employee.sql
-- Core employee roster entity supporting idempotency, version ordering, and field ownership

CREATE TABLE employee (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    source_employee_id VARCHAR(100) NOT NULL,
    source_version BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    manager_source_id VARCHAR(100),
    office_id UUID REFERENCES office(id),
    department VARCHAR(100),
    shift VARCHAR(50),
    pickup_point VARCHAR(255),
    drop_point VARCHAR(255),
    special_assistance VARCHAR(255),
    transport_field_version BIGINT NOT NULL DEFAULT 0,
    record_hash VARCHAR(64) NOT NULL,
    opt_lock_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_tenant_source_emp UNIQUE (tenant_id, source_employee_id)
);

CREATE INDEX idx_employee_tenant_source ON employee(tenant_id, source_employee_id);
CREATE INDEX idx_employee_tenant_status ON employee(tenant_id, status);
CREATE INDEX idx_employee_tenant_hash ON employee(tenant_id, record_hash);

