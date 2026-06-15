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

## Phase 2 — Cognitive Session Management ✅

**Goal:** Multi-turn reasoning sessions persisted and retrievable.

| Deliverable | Status |
|---|---|
| `cognitive_sessions` Flyway migration (V003) | ✅ |
| `CognitiveSessionStore` port interface | ✅ |
| `JdbcCognitiveSessionStore` adapter | ✅ |
| `CognitiveSessionController` (POST create/resume, PUT turns, POST close, GET recent/by-id) | ✅ |
| Active session fast-path lookup (partial index on `active = TRUE`) | ✅ |
| GDPR session erasure (`eraseByUser`) | ✅ |

---

## Phase 3 — GDPR + Right to Erasure ✅

**Goal:** Full GDPR Article 17 compliance — consent management and right to erasure.

| Deliverable | Status |
|---|---|
| `gdpr_consent` Flyway migration (V004) | ✅ |
| `GdprConsent` domain record | ✅ |
| `GdprConsentStore` port interface | ✅ |
| `JdbcGdprConsentStore` adapter | ✅ |
| `GdprController` — GET consent, PUT consent, DELETE erase | ✅ |
| Hard-delete of all user memories, sessions, and consent records | ✅ |
| `GdprConsent.defaultConsent()` / `GdprConsent.optOut()` factory methods | ✅ |

---

## Phase 4 — Grid Feedback Loop (Kafka) ✅

**Goal:** Aether Core consumes Grid decision feedback to reinforce or decay personal memories.

| Deliverable | Status |
|---|---|
| `GridFeedbackConsumer` Kafka listener on `aether.core.feedback` | ✅ |
| `CORRECT` outcome → memory reinforcement | ✅ |
| `INCORRECT` outcome → decay signal logged | ✅ |
| `@ConditionalOnProperty(aether.core.kafka.enabled)` — Kafka optional | ✅ |
| `application.yml` Kafka consumer configuration | ✅ |

---

## Phase 5 — Memory Decay + Reinforcement Scheduler ✅

**Goal:** Memory strength evolves over time — unused memories decay, weak memories are purged.

| Deliverable | Status |
|---|---|
| `MemoryDecayService` with `@Scheduled` daily decay (02:00 UTC) | ✅ |
| Weekly purge pass — memories below threshold hard-deleted (03:00 UTC Sunday) | ✅ |
| Inactive session expiry — sessions idle > 7 days auto-closed | ✅ |
| Configurable decay factor, purge threshold, idle threshold via environment variables | ✅ |
| `@EnableScheduling` on `CoreApiConfig` | ✅ |

---

## Phase 6 — Kubernetes + Helm ✅

**Goal:** Production-ready deployment on Kubernetes (vanilla, AWS EKS, OpenShift).

| Deliverable | Status |
|---|---|
| `core-api/Dockerfile` — multi-stage, Temurin 21 JRE, non-root uid 1000, HEALTHCHECK | ✅ |
| `core-infra/k8s/namespace.yaml` — `aether-core` namespace | ✅ |
| `core-infra/k8s/deployment.yaml` — 2 replicas, securityContext, probes, resource limits | ✅ |
| `core-infra/k8s/service.yaml` — ClusterIP, HPA (min 2 / max 8), ServiceAccount, ConfigMap | ✅ |
| `.github/workflows/ci.yml` — build + test + JaCoCo | ✅ |
| `.github/workflows/docker-build.yml` — OIDC, multi-arch (amd64 + arm64), GHCR push | ✅ |

---

> **Aether Grid Roadmap:** [suplab/aether-grid/docs/roadmap.md](https://github.com/suplab/aether-grid/blob/main/docs/roadmap.md)
