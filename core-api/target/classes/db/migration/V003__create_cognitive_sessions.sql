-- Phase 2: Cognitive Session Management
-- Stores multi-turn reasoning sessions with emotional state tracking.

CREATE TABLE cognitive_sessions (
    session_id       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          TEXT          NOT NULL,
    tenant_id        TEXT          NOT NULL,
    turn_summaries   TEXT[]        NOT NULL DEFAULT '{}',
    emotional_state  TEXT          NOT NULL DEFAULT 'NEUTRAL',
    engagement_score DOUBLE PRECISION NOT NULL DEFAULT 0.5
        CHECK (engagement_score >= 0 AND engagement_score <= 1),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    started_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_active_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Per-user session lookup (most common query pattern)
CREATE INDEX idx_cognitive_sessions_user_tenant
    ON cognitive_sessions (user_id, tenant_id);

-- Active session fast lookup
CREATE INDEX idx_cognitive_sessions_active
    ON cognitive_sessions (user_id, tenant_id)
    WHERE active = TRUE;

-- Session history ordered by recency
CREATE INDEX idx_cognitive_sessions_last_active
    ON cognitive_sessions (user_id, last_active_at DESC);

COMMENT ON TABLE cognitive_sessions IS
    'Multi-turn reasoning sessions tracking emotional arc and engagement per user.';
COMMENT ON COLUMN cognitive_sessions.turn_summaries IS
    'Ordered list of turn summaries added throughout the session interaction.';
COMMENT ON COLUMN cognitive_sessions.emotional_state IS
    'Last known emotional state: NEUTRAL, MOTIVATED, ANXIOUS, FOCUSED, FATIGUED, etc.';
COMMENT ON COLUMN cognitive_sessions.active IS
    'FALSE when the session has been explicitly closed or expired by the scheduler.';
