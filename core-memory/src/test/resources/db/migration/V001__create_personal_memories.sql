-- V001 — Create personal_memories table
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE personal_memories;

CREATE TABLE IF NOT EXISTS personal_memories (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          TEXT         NOT NULL,
    memory_type      TEXT         NOT NULL
                                  CHECK (memory_type IN ('EPISODIC', 'SEMANTIC', 'PROCEDURAL', 'EMOTIONAL')),
    content          TEXT         NOT NULL,
    strength         DOUBLE PRECISION NOT NULL DEFAULT 1.0
                                  CHECK (strength BETWEEN 0 AND 1),
    access_count     INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_accessed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_personal_memories_user_id
    ON personal_memories (user_id);

CREATE INDEX IF NOT EXISTS idx_personal_memories_user_type
    ON personal_memories (user_id, memory_type);

CREATE INDEX IF NOT EXISTS idx_personal_memories_strength
    ON personal_memories (user_id, strength DESC)
    WHERE strength > 0.05;
