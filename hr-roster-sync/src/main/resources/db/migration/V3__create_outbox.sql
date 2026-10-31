-- V3__create_outbox.sql
-- Transactional outbox table for resilient asynchronous downstream notifications

CREATE TABLE outbox_event (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    aggregate_type VARCHAR(50) NOT NULL DEFAULT 'EMPLOYEE',
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_pending_poll ON outbox_event(status, id ASC);
CREATE INDEX idx_outbox_tenant_aggregate ON outbox_event(tenant_id, aggregate_id);

