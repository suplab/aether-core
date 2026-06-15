# Aether Core — Progress Tracker

> **Scope:** This tracker covers **Aether Core** (`suplab/aether-core`) only.
> For Aether Grid progress, see [suplab/aether-grid](https://github.com/suplab/aether-grid).

---

**Active Phase:** Phase 2 — Cognitive Session Management

| Phase | Name | Status | Sessions |
|---|---|---|---|
| 0 | Scaffold | ✅ Complete | 1 |
| 1 | Personal Memory Engine | ✅ Complete | 2 |
| 2 | Cognitive Session Management | 🔄 In Progress | — |
| 3 | GDPR + Right to Erasure | ⏳ Planned | — |
| 4 | Grid Feedback Loop (Kafka) | ⏳ Planned | — |
| 5 | Memory Decay + Reinforcement Scheduler | ⏳ Planned | — |
| 6 | Kubernetes + Helm | ⏳ Planned | — |

---

## Phase 0 — Scaffold ✅

**Commit:** `feat(core): scaffold Aether Core — personal cognitive engine sister project`

### What was done

**Maven project:**
- `pom.xml` — independent parent POM (`aether-core-parent`), Spring Boot 3.3.5 BOM, Java 21, `--enable-preview`, `-parameters` flags
- 4 modules: `core-domain`, `core-memory`, `core-api`, `core-infra`

**`core-domain` — pure domain (no Spring):**
- `PersonalMemory` record: id, userId, MemoryType, content, strength (0–1), accessCount, timestamps; `create()` factory; `reinforce()` returns new instance with strength+0.1
- `MemoryType` enum: EPISODIC, SEMANTIC, PROCEDURAL, EMOTIONAL
- `CognitiveSession` record: sessionId, userId, tenantId, turnSummaries, emotionalState, engagementScore, timestamps
- `PersonalContext` record: userId, tenantId, recentMemorySummaries, preferences, emotionalState, engagementScore, fetchedAt
- `PersonalMemoryStore` port interface: save, findSimilar, findByType, delete, countByUser
- `PersonalContextProvider` port interface: buildContext

**`core-memory` — pgvector adapter + embedding:**
- `PGVectorPersonalMemoryStore`: cosine similarity search (`<=> :query::vector`), explicit column lists, `NamedParameterJdbcTemplate`, `ON CONFLICT` upsert
- `PersonalEmbeddingService`: Ollama REST client (`/api/embeddings`), 384-dim, graceful fallback to zero vector on error

**`core-api` — Spring Boot application:**
- `AetherCoreApplication`: port 8082, `scanBasePackages = "com.suplab.aether.core"`
- `PersonalContextController`: `GET /api/v1/personal-context/{tenantId}/{userId}` — key endpoint consumed by Aether Grid
- `PersonalMemoryController`: `POST /api/v1/users/{userId}/memories`, `GET .../count`, `DELETE .../{memoryId}`
- `CoreApiConfig`: wires `PGVectorPersonalMemoryStore` and `PersonalEmbeddingService` as `@Bean`
- `application.yml`: port 8082, Flyway enabled, Ollama base-url configurable, actuator probes

**`core-infra` — infrastructure:**
- `V001__create_personal_memories.sql`: personal_memories table with indexes
- `V002__pgvector_personal_embeddings.sql`: pgvector extension, vector(384) column, ivfflat index
- `docker/docker-compose.yml`: postgres-core (port 5433) + aether-core (port 8082)

**`.claude/` setup:**
- 19 agent definitions
- 7 memory files seeded with Core context
- `CLAUDE.md` project brief

**Docs:**
- `README.md`, `docs/index.html`, `docs/architecture.md`, `docs/roadmap.md`, `docs/progress.md`
- GitHub Actions: `ci.yml`, `quality-gate.yml`

### Files created: 57

---

## Phase 1 — Personal Memory Engine ✅

**Commit:** `feat(core): Phase 1 — personal memory engine with reinforce-on-read and context provider`

### What was done

**Reinforce-on-read in `PGVectorPersonalMemoryStore`:**
- `findSimilar()` and `findByType()` now call `memory.reinforce()` on each returned result
- Reinforced state (strength +0.1 capped at 1.0, accessCount +1, lastAccessedAt = now) persisted immediately via UPDATE
- Extracted `mapRow()` helper to eliminate duplication
- `reinforceAndPersist()` private method handles the UPDATE without re-embedding

**`DefaultPersonalContextProvider` — new class in `core-memory`:**
- `com.suplab.aether.core.memory.context.DefaultPersonalContextProvider`
- Implements `PersonalContextProvider` port from `core-domain`
- Fetches EPISODIC + SEMANTIC memories for summaries, EMOTIONAL memories for state
- Returns `Optional.empty()` when user has zero memories across all types
- Engagement score = average of episodic memory strengths (default 0.5 when no episodic memories)

**`PersonalContextController` — refactored to use `PersonalContextProvider` port:**
- Removed direct `PersonalMemoryStore` and `PersonalEmbeddingService` dependencies from controller
- Now injects only `PersonalContextProvider` — single responsibility
- Falls back to `emptyContext()` (NEUTRAL, 0.5) when provider returns empty
- Always HTTP 200 — Grid callers always receive a usable response

**`CoreApiConfig` — updated:**
- `PersonalContextProvider` bean wired: `DefaultPersonalContextProvider(memoryStore, defaultMemoryLimit)`
- `@ConditionalOnProperty(name = "aether.core.embedding.enabled", havingValue = "true", matchIfMissing = true)` on embedding bean
- `aether.core.context.memory-limit` config property (default 5)

**`PersonalMemoryController` — optional embedding:**
- `Optional<PersonalEmbeddingService>` via constructor injection
- When embedding disabled (`aether.core.embedding.enabled=false`), stores zero-vector — other endpoints remain functional

**Unit tests — 18 tests, all green:**
- `PersonalMemoryTest` (12 tests): `create()`, `reinforce()`, validation, all MemoryType values
- `DefaultPersonalContextProviderTest` (6 tests): empty/non-empty contexts, emotional state derivation, engagement score calculation, user/tenant isolation

**Testcontainers integration test — `PGVectorPersonalMemoryStoreIT`:**
- `pgvector/pgvector:pg16` container
- Flyway migrations run in-test
- Tests: save+findByType round-trip, reinforce-on-read (strength progression), findSimilar returns reinforced, countByUser, cross-user isolation, upsert semantics
- Runs in CI (Docker unavailable in local scaffold env)

**JaCoCo 80% line coverage gate:**
- Added to parent `pom.xml` pluginManagement
- `prepare-agent` → `report` → `check` at `verify` phase
- `argLine` property defaulted to empty to prevent `@{argLine}` resolution failure

**`application.yml` additions:**
- `aether.core.embedding.enabled: ${EMBEDDING_ENABLED:true}`
- `aether.core.context.memory-limit: ${CONTEXT_MEMORY_LIMIT:5}`

### Files changed: 9 | Tests added: 18 + 9 IT scenarios
