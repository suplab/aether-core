# Aether Core — Architecture

> **Sister repository:** [suplab/aether-grid](https://github.com/suplab/aether-grid) — the enterprise agent mesh that consumes Core's personal context API.

---

## Overview

Aether Core is the personal cognitive engine of the Aether ecosystem. It stores individual memories across four types, assembles personal context snapshots on-demand, and exposes a REST API that Aether Grid agents call to enrich enterprise decisions with individual user context.

```
Aether Grid (suplab/aether-grid)
      │
      │  GET /api/v1/personal-context/{tenantId}/{userId}
      ▼
Aether Core (suplab/aether-core)          port 8082
      │
      ├── PersonalContextController
      │       │
      │       ├── PersonalMemoryStore (port)
      │       │       └── PGVectorPersonalMemoryStore (adapter)
      │       │                └── PostgreSQL 16 + pgvector
      │       │
      │       └── PersonalEmbeddingService
      │               └── Ollama (all-MiniLM-L6-v2, 384-dim)
      │
      └── PersonalMemoryController
              └── POST/DELETE memories
```

---

## Module Boundaries

### `core-domain` — Pure Domain (No Spring)

Contains all domain types and port interfaces. Zero Spring dependencies — fully unit-testable without a context.

```
com.suplab.aether.core.domain
  PersonalMemory        — record: id, userId, type, content, strength, accessCount, timestamps
  MemoryType            — enum: EPISODIC | SEMANTIC | PROCEDURAL | EMOTIONAL
  CognitiveSession      — record: sessionId, userId, tenantId, turnSummaries, emotionalState, ...
  PersonalContext       — record: assembled snapshot served to Grid

com.suplab.aether.core.ports
  PersonalMemoryStore   — driven port: save, findSimilar, findByType, delete, countByUser
  PersonalContextProvider — driven port: buildContext(tenantId, userId)
```

### `core-memory` — Persistence Adapters

Implements the port interfaces from `core-domain`. Depends on Spring JDBC and pgvector.

```
com.suplab.aether.core.memory.store
  PGVectorPersonalMemoryStore  — implements PersonalMemoryStore
    • save(): upsert with vector embedding
    • findSimilar(): cosine similarity (<=>), ORDER BY distance, LIMIT
    • findByType(): filtered by memory_type, ORDER BY strength DESC

com.suplab.aether.core.memory.embedding
  PersonalEmbeddingService  — Ollama RestClient adapter
    • embed(text) → float[384]
    • Graceful fallback: returns zero vector on Ollama unavailability
```

### `core-api` — Spring Boot Application

Running application (port 8082). Wires all modules, exposes REST endpoints, runs Flyway.

```
com.suplab.aether.core.api
  AetherCoreApplication  — @SpringBootApplication, port 8082

com.suplab.aether.core.api.controller
  PersonalContextController  — GET /api/v1/personal-context/{tenantId}/{userId}
  PersonalMemoryController   — POST/GET/DELETE /api/v1/users/{userId}/memories

com.suplab.aether.core.api.config
  CoreApiConfig  — @Bean wiring: PersonalMemoryStore, PersonalEmbeddingService
```

### `core-infra` — Infrastructure

Docker Compose for local dev, standalone Flyway migrations.

---

## Database Schema

### `personal_memories` table

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK, `gen_random_uuid()` |
| `user_id` | `TEXT` | Scoped per user — never cross-user queries |
| `memory_type` | `TEXT` | CHECK IN ('EPISODIC','SEMANTIC','PROCEDURAL','EMOTIONAL') |
| `content` | `TEXT` | Raw memory text |
| `embedding` | `vector(384)` | all-MiniLM-L6-v2 cosine-similarity index |
| `strength` | `DOUBLE PRECISION` | 0.0–1.0, reinforced on access |
| `access_count` | `INTEGER` | Incremented on each read |
| `created_at` | `TIMESTAMPTZ` | Immutable |
| `last_accessed_at` | `TIMESTAMPTZ` | Updated on reinforce |

**Indexes:**
- `idx_personal_memories_user_id` — `(user_id)` for per-user queries
- `idx_personal_memories_user_type` — `(user_id, memory_type)` for type-filtered queries
- `idx_personal_memories_embedding` — `ivfflat (embedding vector_cosine_ops)`, lists=100

---

## PersonalContext API Contract

This is the contract between Aether Core and Aether Grid. **Breaking changes require Grid-side updates.**

```
GET /api/v1/personal-context/{tenantId}/{userId}?memoryLimit=5

Response 200:
{
  "userId": "user-42",
  "tenantId": "acme-corp",
  "recentMemorySummaries": [
    "Presented Q3 roadmap to stakeholders",
    "Prefers async communication over meetings"
  ],
  "preferences": {},
  "emotionalState": "MOTIVATED",
  "engagementScore": 0.82,
  "fetchedAt": "2026-06-15T08:00:00Z"
}
```

---

## Aether Grid Integration

Grid's `AetherCoreBridgeAgent` (in `suplab/aether-grid`) calls this endpoint before agent decisions. Configuration in Grid:

```yaml
aether:
  core:
    base-url: http://aether-core:8082
    api-key: ${AETHER_CORE_API_KEY}
    connect-timeout-ms: 3000
    read-timeout-ms: 5000
```

Grid's `AetherCoreHttpAdapter` has a Resilience4j circuit breaker (`aether-core`) — when Core is unavailable, Grid falls back to `Optional.empty()` and proceeds without personal context (degraded but not blocked).

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (`--enable-preview`) |
| Framework | Spring Boot 3.3.5 (`jakarta.*`) |
| Database | PostgreSQL 16 + pgvector |
| Vector Search | pgvector, 384-dim, ivfflat cosine index |
| Embedding | all-MiniLM-L6-v2 via Ollama |
| DB Migrations | Flyway |
| Build | Maven multi-module |
| Local Dev | Docker Compose |
