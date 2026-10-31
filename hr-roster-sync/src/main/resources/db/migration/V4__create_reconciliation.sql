-- V4__create_reconciliation.sql
-- Reconciliation run tracking, safety brake metrics, and audit history

CREATE TABLE reconciliation_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    total_in_file INT NOT NULL DEFAULT 0,
    total_in_store INT NOT NULL DEFAULT 0,
    added_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    deactivated_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    missing_ratio DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    brake_triggered BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by VARCHAR(100),
    file_version BIGINT NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_recon_tenant_status ON reconciliation_run(tenant_id, status);

CREATE TABLE reconciliation_item (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES reconciliation_run(id) ON DELETE CASCADE,
    source_employee_id VARCHAR(100) NOT NULL,
    action VARCHAR(30) NOT NULL,
    reason TEXT,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recon_item_run ON reconciliation_item(run_id);

