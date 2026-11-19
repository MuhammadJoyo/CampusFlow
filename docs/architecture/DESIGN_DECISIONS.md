# Design Decision Log
## CampusFlow — Smart Campus Helpdesk & Queue Management System

> **Document ID**: ARCH-003
> **Version**: 1.1
> **Date**: 2026-11-19
> **Purpose**: Permanent record of all architectural and design decisions, the
> context in which they were made, options considered, and their consequences.
> Updated after each approval gate.

---

## Decision Record Format

Each record follows the **Architecture Decision Record (ADR)** format:

| Field | Meaning |
|---|---|
| **Status** | `Approved` / `Superseded` / `Deprecated` |
| **Context** | Why was a decision needed? |
| **Options** | What alternatives were considered? |
| **Decision** | What was chosen? |
| **Rationale** | Why was this chosen over alternatives? |
| **Consequences** | What does this decision imply for implementation? |

---

## ADR-001 — Separate Server Process Architecture

| Field | Detail |
|---|---|
| **Status** | ✅ Approved — 2026-11-19 |
| **Context** | The application needs a central helpdesk engine to manage queues, route tickets, and persist data. The question was whether to embed the server in one of the client processes or run it independently. |
| **Options** | (A) Auto-start server inside the first client process (B) Separate server Java process (C) Embedded server started by Admin client only |
| **Decision** | **Option B — Separate Java server process** |
| **Rationale** | A separate server process provides true client independence, allows multiple simultaneous client connections from different machines, and makes the client-server boundary explicit and demonstrable. It also means no client crash can bring down the server. |
| **Consequences** | • `HelpdeskServer` runs as a standalone process (its own `main()`) • Students, Staff, and Admin use separate JavaFX client processes • `server.properties` configures the server independently of clients • Windows `.bat` scripts provided for server and client startup • Server supports graceful shutdown via `Runtime.addShutdownHook()` • SQLite database is exclusively managed by the server process |

---

## ADR-002 — TQL Access for All Roles with Server-Side Enforcement

| Field | Detail |
|---|---|
| **Status** | ✅ Approved — 2026-11-19 |
| **Context** | The Ticket Query Language (TQL) feature needed a scope decision: which user roles may use it, and where ticket ownership is enforced. |
| **Options** | (A) All roles — server enforces ownership (B) Staff and Admin only (C) Admin only |
| **Decision** | **Option A — All roles; server-side ownership enforcement** |
| **Rationale** | Broader use maximises demonstrable value of the ANTLR component. Server-side enforcement is mandatory regardless of UI — a rogue client sending `GET_ALL_TICKETS` without authorisation must receive only their own data. |
| **Consequences** | • `TicketRepository.search(filter, role, userId)` appends ownership `WHERE` clause for Students: `AND t.submitter_id = ?` • TQL search bar visible on Student, Staff, and Admin dashboards • Server enforces scoping even if the client sends no ownership filter • Students cannot receive another student's tickets via any TQL query • Traceability Matrix SCD-08 updated to reflect server-side enforcement |

---

## ADR-003 — Fixed 30-Minute Session Expiry with ScheduledExecutorService

| Field | Detail |
|---|---|
| **Status** | ✅ Approved — 2026-11-19 |
| **Context** | Sessions stored in `ConcurrentHashMap` could grow indefinitely if clients disconnect without logging out (crash, network drop). A cleanup strategy was required. |
| **Options** | (A) Explicit logout only — no automatic expiry (B) Fixed 30-minute inactivity timeout with periodic sweeper (C) Rolling idle timeout with per-request timestamp update |
| **Decision** | **Option B — Fixed 30-minute inactivity timeout** |
| **Rationale** | A fixed timeout with a periodic sweeper (`ScheduledExecutorService`, 5-minute interval) is simple, predictable, handles crash-without-logout, and demonstrates an additional SCD concurrency concept without the per-request write contention of Option C. |
| **Consequences** | • `Session` stores `lastAccessedAt: LocalDateTime` • `AuthService.validateSession()` updates `lastAccessedAt` on every successful validation • `AuthService` starts a `ScheduledExecutorService` (single daemon thread) on server startup • Sweeper calls `AuthService.cleanExpiredSessions()` every 5 minutes • Sessions older than 30 minutes of inactivity are silently removed • If a client's session is swept, the next request returns `SESSION_EXPIRED`; client redirects to login screen • Timeout value read from `server.properties` key `server.session.timeout.minutes=30` |

---

## ADR-004 — jBCrypt for Password Hashing

| Field | Detail |
|---|---|
| **Status** | ✅ Approved — 2026-11-19 |
| **Context** | User passwords must never be stored in plaintext. A hashing strategy was required. |
| **Options** | (A) `org.mindrot:jbcrypt:0.4` — purpose-built BCrypt, 17 KB, zero transitive deps (B) `spring-security-crypto` — pulls Spring Core (~1.5 MB) as transitive dep (C) SHA-256 + salt via `java.security.MessageDigest` — built-in but not adaptive |
| **Decision** | **Option A — `org.mindrot:jbcrypt:0.4`** |
| **Rationale** | Smallest footprint, zero transitive dependencies, purpose-built for BCrypt, two-line API (`BCrypt.hashpw()` and `BCrypt.checkpw()`). Spring is architecturally inappropriate for this project. SHA-256 is not an adaptive hash and is demonstrably weaker. |
| **Consequences** | • `org.mindrot:jbcrypt:0.4` added to `pom.xml` with `compile` scope • `PasswordUtil.hash(plaintext)` wraps `BCrypt.hashpw(plaintext, BCrypt.gensalt(12))` • `PasswordUtil.verify(plaintext, hash)` wraps `BCrypt.checkpw(plaintext, hash)` • Passwords are never logged or returned in any API response • `users.password_hash` column always contains a BCrypt `$2a$12$...` string • Admin seed account hash in DDL must be replaced with a real BCrypt hash before first run |

---

## ADR-005 — server.properties Configuration File

| Field | Detail |
|---|---|
| **Status** | ✅ Approved — 2026-11-19 |
| **Context** | Several values (port, thread pool size, session timeout) needed to be configurable without recompilation, as required by NFR-09. |
| **Options** | (A) Hardcoded constants in source (B) `server.properties` file in `src/main/resources/` (C) Command-line arguments via `args[]` |
| **Decision** | **Option B — `server.properties` in `src/main/resources/`** |
| **Rationale** | Satisfies NFR-09 exactly. No argument parsing library needed. Bundled in the JAR by Maven. `ClassLoader.getResourceAsStream()` loads it at startup. Easy to demonstrate and explain. |
| **Consequences** | • File location: `src/main/resources/server.properties` • Contents: `server.port=9090`, `server.thread.pool.size=20`, `server.session.timeout.minutes=30`, `server.db.path=campusflow.db` • `DatabaseManager`, `HelpdeskServer`, and `AuthService` read this file at startup • Thread pool size and session timeout are runtime-configurable without recompilation • Port change requires server restart • Client must be told the correct port (default 9090 is documented in startup scripts) |

---

## ADR-006 — Newline-Delimited UTF-8 JSON Protocol

| Field | Detail |
|---|---|
| **Status** | ✅ Approved — 2026-11-19 (Phase 1 architecture) |
| **Context** | A message framing strategy was needed for TCP socket communication. |
| **Options** | (A) Binary serialisation (Java `ObjectOutputStream`) (B) Length-prefixed binary frames (C) Newline-delimited UTF-8 JSON strings |
| **Decision** | **Option C — Newline-delimited UTF-8 JSON** |
| **Rationale** | Human-readable and debuggable with `telnet` / `netcat`. No serialisation library required at this phase. `BufferedReader.readLine()` / `PrintWriter.println()` provide natural framing. |
| **Consequences** | • All message fields serialised as strings; no binary data in protocol • Message payload values must escape JSON-special characters • Newline character `\n` must not appear in any field value |

---

## ADR-007 — PriorityBlockingQueue per Department

| Field | Detail |
|---|---|
| **Status** | ✅ Approved — 2026-11-19 (Phase 1 architecture) |
| **Context** | Tickets must be served in priority order. The question was whether to use a single global queue or one queue per department. |
| **Options** | (A) Single global `PriorityBlockingQueue` for all departments (B) One `PriorityBlockingQueue` per department |
| **Decision** | **Option B — One queue per department** |
| **Rationale** | Allows staff in different departments to dequeue independently without contention. `dequeue(department)` is O(log n) on a small per-department queue rather than on a large global one. Natural workload isolation — an IT queue backlog doesn't block Maintenance staff. |
| **Consequences** | • `TicketQueueManager` holds a `Map<String, PriorityBlockingQueue<Ticket>>` • Departments are loaded from DB at startup; queues created dynamically • `Ticket implements Comparable<Ticket>` — by priority ordinal then `createdAt` |

---

## ADR-008 — ANTLR4 for TQL Parser Generator

| Field | Detail |
|---|---|
| **Status** | ✅ Approved — 2026-11-19 (Phase 1 architecture) |
| **Context** | The TQL feature requires parsing structured query strings. A parsing strategy was needed. |
| **Options** | (A) Manual recursive-descent parser (B) ANTLR4 grammar + generated parser (C) Simple string-split approach |
| **Decision** | **Option B — ANTLR4** |
| **Rationale** | Directly demonstrates SCD parser generator and grammar concepts. Grammar (`TicketQuery.g4`) is maintained separately from parser code, making it extensible. ANTLR4 provides error recovery, token position reporting, and a clean visitor pattern at zero runtime overhead. |
| **Consequences** | • `org.antlr:antlr4-runtime:4.13.1` added to `pom.xml` in implementation phase • `antlr4-maven-plugin` generates lexer + parser + visitor at `generate-sources` phase • Grammar file: `src/main/antlr4/com/campusflow/tql/TicketQuery.g4` • Generated sources: `target/generated-sources/antlr4/` |

---

## ADR-009 — In-Memory SQLite for Unit Tests

| Field | Detail |
|---|---|
| **Status** | ✅ Approved — 2026-11-19 (Phase 1 architecture) |
| **Context** | Repository tests need a database but should not create files on disk or require cleanup. |
| **Decision** | Use `jdbc:sqlite::memory:` connection URL for all test repository classes |
| **Rationale** | Zero file I/O, instant schema creation, each test method gets a fresh schema via `@BeforeEach` setup, no cleanup required, runs the same JDBC code as production. |
| **Consequences** | • `TestDatabaseManager` or `@BeforeEach` creates schema + seed data in memory • Production DB file `campusflow.db` is never touched during `mvn test` • No test isolation concern — in-memory DB is fresh per test class |

---

## ADR-010 — ReentrantReadWriteLock for Analytics Cache

| Field | Detail |
|---|---|
| **Status** | ✅ Approved — 2026-11-19 (Phase 1 architecture) |
| **Context** | The analytics dashboard is expensive to compute (multiple aggregate queries). Multiple admin clients may request it simultaneously. Cache invalidation must be thread-safe. |
| **Options** | (A) `synchronized` on `AnalyticsService` — simple but serialises all readers (B) `ReentrantReadWriteLock` — concurrent reads, exclusive writes |
| **Decision** | **Option B — `ReentrantReadWriteLock`** |
| **Rationale** | Multiple admin dashboards can read the cached report simultaneously. Only a ticket status change (which is rare relative to reads) needs an exclusive write lock for cache invalidation. Directly demonstrates SCD Lock concepts. |
| **Consequences** | • `readLock()` acquired/released for every cache read • `writeLock()` acquired/released for cache invalidation and refresh • `TicketService` calls `AnalyticsService.invalidateCache()` after every write operation • Cache expiry tracked by `LocalDateTime cacheExpiry` field |

---

## Document History

| Version | Date | Author | Change |
|---|---|---|---|
| 1.0 | 2026-11-19 | Project Team | Initial design decision log — Phase 1 architecture decisions (ADR-006 through ADR-010) |
| 1.1 | 2026-11-19 | Project Team | Added Phase 2 approved decisions: ADR-001 (server process), ADR-002 (TQL all roles), ADR-003 (session expiry), ADR-004 (jBCrypt), ADR-005 (server.properties) |
