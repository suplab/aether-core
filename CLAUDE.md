# CLAUDE.md — Aether Core Project Brief

> Read this at the start of every session. Single source of truth for what this project is, how it is built, and what rules apply.

---

## What This Project Is

**Aether Core** (`suplab/aether-core`) is the personal cognitive engine of the Aether ecosystem — the individual "mind OS" that manages personal memories, reasoning sessions, and emotional context for each user.

> **Ecosystem navigation**
>
> | Layer | Repo | Purpose |
> |---|---|---|
> | Aether Philosophy | — | The vision: cognitive fabric connecting humans, memory, and AI |
> | **Aether Core** | `suplab/aether-core` ← **you are here** | Personal cognitive engine — individual memory, reasoning, emotional context |
> | **Aether Grid** | [`suplab/aether-grid`](https://github.com/suplab/aether-grid) | Distributed agent mesh — enterprise API governance platform |

**Sister repository:** [`suplab/aether-grid`](https://github.com/suplab/aether-grid) — the enterprise agent mesh that consumes Core's `GET /api/v1/personal-context/{tenantId}/{userId}` endpoint via `AetherCoreHttpAdapter`.

**Current status:** Phases 0–1 complete. Active phase: Phase 2 — Cognitive Session Management.

**One runnable application:**
- `core-api` — Personal Cognitive Engine API (port 8082)

**Three library modules:** `core-domain`, `core-memory`, `core-infra`

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 (`jakarta.*` exclusively — never `javax.*`) |
| Database | PostgreSQL 16 + pgvector extension |
| Vector Store | pgvector (384-dim, all-MiniLM-L6-v2) |
| Embedding | all-MiniLM-L6-v2 via Ollama (`/api/embeddings`) |
| LLM Runtime | Ollama (local, model-agnostic) |
| DB Migrations | Flyway (classpath:db/migration in core-api) |
| Build | Maven (multi-module, Java 21, --enable-preview) |
| Local Dev | Docker Compose (`core-infra/docker/docker-compose.yml`) |
| CI/CD | GitHub Actions (OIDC, SHA-pinned actions) |

---

## Bounded Context

- Package root: `com.suplab.aether.core`
- Port: **8082** (Grid proxy=8080, Grid api=8081, Core=8082)
- Database: `aether_core` (separate schema from Aether Grid — data isolation)
- Kafka topics consumed (Phase 4): `aether.core.feedback` (Grid decision feedback for personal learning)
- REST API contract with Grid: `GET /api/v1/personal-context/{tenantId}/{userId}`

---

## Module Structure

```
aether-core-parent (pom.xml)
├── core-domain     — domain types (PersonalMemory, CognitiveSession, PersonalContext) + port interfaces
├── core-memory     — pgvector store + Ollama embedding service
├── core-api        — Spring Boot REST API, Flyway migrations, config
└── core-infra      — Docker Compose, migration reference copies (no Java sources)
```

### Dependency Graph

```
core-api
  ├── core-domain
  └── core-memory
        └── core-domain
core-infra  (no Java)
```

`core-domain` has no framework dependency — pure Java 21 records and interfaces.

---

## Pre-Coding Checklist

Before writing any code:
- [ ] Which module does this change belong to? Does it respect bounded context?
- [ ] Is there an existing port interface or utility to reuse?
- [ ] Does this change require a new Flyway migration?
- [ ] Does this change affect the data model or API contract? → update `docs/architecture.md`
- [ ] Does this change affect the roadmap status? → update `docs/progress.md` and `docs/roadmap.md`

---

## Ten Golden Rules (Non-Negotiable)

1. **Constructor injection exclusively** — no field-level `@Autowired`, no `@Inject`, fields must be `final`
2. **No hardcoded secrets** — all credentials to environment variables; never committed to source
3. **SLF4J with parameterized messages** — never `System.out.println()` or string concatenation in logs
4. **SOLID design principles** — single responsibility, open/closed, Liskov, interface segregation, dependency inversion
5. **DDD bounded contexts** — cross-module calls go through port interfaces, never reach into another module's internals
6. **Explicit column lists in SQL** — never `SELECT *`; always name every column
7. **Parameterized queries only** — no string concatenation for SQL; use `NamedParameterJdbcTemplate`
8. **Conventional Commits** — `type(scope): description` (feat, fix, docs, chore, build, test, refactor)
9. **No `// TODO` in committed code** — if it's not done, don't commit it
10. **`jakarta.*` exclusively** — Spring Boot 3.x; `javax.*` imports are a build-breaking error

### Aether Core-Specific Constraints

- All memory data scoped to `userId` in every SQL query — no cross-user data access
- Embedding dimension is 384 (all-MiniLM-L6-v2) — changing requires a full re-embedding migration
- Grid integration is optional — Core must run standalone without Grid being present
- Memory embeddings: never store raw PII without user consent
- Ollama must be replaceable: all embedding calls go through `PersonalEmbeddingService` (not direct HTTP)

---

## Slash Commands

| Command | Purpose |
|---|---|
| `/estimate` | P50/P80/P90 effort estimate (Human Days = Raw Hours / 6.4) |
| `/review` | Code review against golden rules |
| `/adr` | Create an Architecture Decision Record |
| `/security-scan` | Security review of current changes |
| `/memory-update` | Update `.claude/memory/` files after major decisions |

---

## Memory Files

| File | Contents |
|---|---|
| `project-context.md` | Core service details, ports, environments |
| `domain-glossary.md` | Aether Core terminology |
| `decisions.md` | Architecture decisions log |
| `constraints.md` | Hard constraints + golden rules |
| `patterns.md` | Approved patterns in use |
| `tech-debt.md` | Known technical debt |
| `session-log.md` | Rolling session log |

---

## Prohibited Patterns

- `javax.*` in any Spring Boot 3.x file
- Field `@Autowired` or `@Inject`
- `SELECT *` in any SQL
- Hardcoded passwords, tokens, or connection strings
- `Thread.sleep()` in tests (use Awaitility or Testcontainers)
- Empty `catch` blocks
- `Optional.get()` without guard
- `System.out.println()` in any production code
- Cross-user data access (missing `user_id` in WHERE clause)

---

## Documentation Sync Rule

Every commit that changes system behavior MUST update:
- `docs/progress.md` — mark completed deliverables
- `README.md` — if architecture or scope changed
- `docs/index.html` — if conceptual overview or tech stack changed
- `docs/roadmap.md` — if milestones shift
- `docs/architecture.md` — if architectural decisions change
