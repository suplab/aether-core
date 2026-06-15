-- Phase 3: GDPR Consent and Right to Erasure
-- Tracks per-user memory storage consent for GDPR compliance.

CREATE TABLE gdpr_consent (
    user_id                TEXT        NOT NULL,
    tenant_id              TEXT        NOT NULL,
    memory_storage_allowed BOOLEAN     NOT NULL DEFAULT TRUE,
    data_retention_days    INTEGER     NOT NULL DEFAULT 365
        CHECK (data_retention_days >= 0),
    consent_recorded_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, tenant_id)
);

-- Lookup by tenant for bulk compliance reporting
CREATE INDEX idx_gdpr_consent_tenant
    ON gdpr_consent (tenant_id);

COMMENT ON TABLE gdpr_consent IS
    'GDPR consent preferences per user/tenant. Opt-out blocks memory storage.';
COMMENT ON COLUMN gdpr_consent.memory_storage_allowed IS
    'FALSE = no memories will be stored and existing memories will be scheduled for erasure.';
COMMENT ON COLUMN gdpr_consent.data_retention_days IS
    'How long memories may be retained. 0 = immediate erasure required.';
