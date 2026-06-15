# Aether Core — Development Roadmap

> **Scope:** This roadmap covers Aether Core only.
> For Aether Grid roadmap, see [suplab/aether-grid/docs/roadmap.md](https://github.com/suplab/aether-grid/blob/main/docs/roadmap.md).

---

## Phase 0 — Scaffold ✅

**Goal:** Standalone project bootstrapped. Independent Maven multi-module, Spring Boot 3.3.5, all golden rules enforced, sister repo relationship established.

| Deliverable | Status |
|---|---|
| Independent parent POM (not child of Grid) | ✅ |
| 4 Maven modules: core-domain, core-memory, core-api, core-infra | ✅ |
| Domain model: PersonalMemory, MemoryType, CognitiveSession, PersonalContext | ✅ |
| Port interfaces: PersonalMemoryStore, PersonalContextProvider | ✅ |
| PGVectorPersonalMemoryStore adapter | ✅ |
| PersonalEmbeddingService (Ollama all-MiniLM-L6-v2, 384-dim) | ✅ |
| PersonalContextController (`GET /api/v1/personal-context/{tenantId}/{userId}`) | ✅ |
| PersonalMemoryController (POST, GET count, DELETE) | ✅ |
| Flyway migrations V001 + V002 (pgvector schema) | ✅ |
| Docker Compose (postgres-core + aether-core) | ✅ |
| GitHub Actions CI + quality-gate | ✅ |
| CLAUDE.md + .claude/memory/ (7 files) + .claude/agents/ (19 agents) | ✅ |
| Docs: README, index.html, architecture.md, roadmap.md, progress.md | ✅ |

---

## Phase 1 — Personal Memory Engine ✅

**Goal:** Memory store fully operational with reinforcement-on-read, integration tests, and a working `PersonalContextProvider` implementation.

| Deliverable | Status |
|---|---|
| Reinforce-on-read in `PGVectorPersonalMemoryStore` | ✅ |
| `PersonalContextProvider` implementation in core-memory | ✅ |
| Testcontainers integration test: save + findSimilar round-trip | ✅ |
| `PersonalContextController` uses `PersonalContextProvider` port | ✅ |
| `@ConditionalOnProperty` for embedding (skip when Ollama unavailable) | ✅ |
| Unit tests for PersonalMemory domain logic | ✅ |
| JaCoCo 80% line coverage gate | ✅ |

---

## Phase 2 — Cognitive Session Management

**Goal:** Multi-turn reasoning sessions persisted and retrievable. Session context included in `PersonalContext` response.

| Deliverable | Status |
|---|---|
| `cognitive_sessions` Flyway migration (V003) | ⏳ |
| `CognitiveSessionStore` port interface | ⏳ |
| `JdbcCognitiveSessionStore` adapter | ⏳ |
| `CognitiveSessionController` (POST create, GET by userId, PATCH add turn) | ⏳ |
| `PersonalContext` enriched with active session's turn summaries | ⏳ |
| User preferences table (V004) | ⏳ |

---

## Phase 3 — GDPR + Privacy Controls

**Goal:** Full right-to-erasure, data retention configuration, and memory export.

| Deliverable | Status |
|---|---|
| `DELETE /api/v1/users/{userId}/memories` — erase all memories | ⏳ |
| `DELETE /api/v1/users/{userId}` — full account erasure | ⏳ |
| `data_retention_days` per user configurable | ⏳ |
| Memory export: `GET /api/v1/users/{userId}/export` (JSON) | ⏳ |
| Audit log for erasure events | ⏳ |
| V005 migration: user_privacy_settings table | ⏳ |

---

## Phase 4 — Grid Feedback Loop (Kafka)

**Goal:** Aether Core consumes Grid decision feedback to improve personal context relevance.

| Deliverable | Status |
|---|---|
| Kafka consumer for `aether.core.feedback` topic | ⏳ |
| `AgentDecisionFeedbackProcessor`: maps Grid outcomes to memory reinforcement/creation | ⏳ |
| `PROCEDURAL` memory auto-created from correct Grid decisions | ⏳ |
| `EMOTIONAL` memory updated from engagement signals | ⏳ |
| Docker Compose Kafka service added | ⏳ |

---

## Phase 5 — Memory Decay + Reinforcement Scheduler

**Goal:** Memory strength evolves over time — unused memories decay, accessed memories reinforce.

| Deliverable | Status |
|---|---|
| `@Scheduled` decay job: `strength -= 0.01 * days_since_access` for memories older than 7 days | ⏳ |
| Configurable decay rate (`aether.core.memory.decay-rate`) | ⏳ |
| Memories below `strength < 0.1` archived (not deleted) | ⏳ |
| Memory archive table (V006 migration) | ⏳ |
| Micrometer metrics: `aether.core.memories.total`, `aether.core.memories.decayed` | ⏳ |

---

## Phase 6 — Kubernetes + Helm

**Goal:** Production-ready deployment for Core on Kubernetes (vanilla, AWS EKS, OpenShift).

| Deliverable | Status |
|---|---|
| `core-api/Dockerfile` (multi-stage, Temurin 21 JRE, non-root uid 1000) | ⏳ |
| Helm chart: `core-infra/helm/aether-core/` | ⏳ |
| `values.yaml`, `values-aws.yaml`, `values-openshift.yaml` | ⏳ |
| GitHub Actions Docker build + Helm release workflows | ⏳ |
| HPA (min 2, max 4 replicas) | ⏳ |

---

> **Aether Grid Roadmap:** [suplab/aether-grid/docs/roadmap.md](https://github.com/suplab/aether-grid/blob/main/docs/roadmap.md)
