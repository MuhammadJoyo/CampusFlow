# Requirement-to-SCD Concept Traceability Matrix
## CampusFlow — Smart Campus Helpdesk & Queue Management System

> **Document ID**: REQ-003
> **Version**: 1.0
> **Date**: 2026-11-19
> **Purpose**: Map every functional requirement and non-functional requirement to
> the Software Construction and Development (SCD) concepts it exercises, and
> specify exactly how each concept is implemented.

---

## 1. SCD Concept Registry

The following SCD concepts are tracked throughout this matrix:

| Code | SCD Concept | Implementation in CampusFlow |
|---|---|---|
| **SCD-01** | Java Sockets | `java.net.ServerSocket`, `java.net.Socket`, TCP client-server |
| **SCD-02** | Concurrency / Multithreading | `ExecutorService`, fixed thread pool, `Runnable` per client |
| **SCD-03** | Thread Safety | Shared state protected by concurrency primitives |
| **SCD-04** | Locks | `ReentrantLock`, `ReentrantReadWriteLock` |
| **SCD-05** | Blocking Queues | `PriorityBlockingQueue<Ticket>` per department |
| **SCD-06** | Parser Generators | ANTLR4 — generates `TicketQueryLexer` + `TicketQueryParser` from grammar |
| **SCD-07** | Formal Grammars | `TicketQuery.g4` — defines TQL syntax with lexer and parser rules |
| **SCD-08** | Little Languages | TQL (Ticket Query Language) — domain-specific filter language |
| **SCD-09** | OOP Inheritance | `Student`, `StaffMember`, `Admin` extend `User` |
| **SCD-10** | OOP Polymorphism | `Ticket implements Comparable<Ticket>`; role-polymorphic dispatch |
| **SCD-11** | Design Patterns | Repository, DTO, Strategy, Template Method, Singleton, Observer |
| **SCD-12** | JDBC / SQLite | `sqlite-jdbc`; transactions; prepared statements; `PRAGMA foreign_keys` |
| **SCD-13** | Unit Testing (JUnit 5) | Tests for services, repositories, TQL parser, queue manager |
| **SCD-14** | JavaFX UI | FXML controllers; `Task<>`/`Platform.runLater()` for background socket I/O |

---

## 2. Functional Requirement → SCD Concept Mapping

| FR ID | Requirement Name | SCD-01 | SCD-02 | SCD-03 | SCD-04 | SCD-05 | SCD-06 | SCD-07 | SCD-08 | SCD-09 | SCD-10 | SCD-11 | SCD-12 | SCD-13 | SCD-14 |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| FR-01 | User Account Creation | ✓ | | | | | | | | ✓ | | ✓ | ✓ | ✓ | ✓ |
| FR-02 | User Authentication | ✓ | ✓ | ✓ | | | | | | ✓ | | ✓ | ✓ | ✓ | ✓ |
| FR-03 | Complaint Submission | ✓ | ✓ | ✓ | | ✓ | | | | | | ✓ | ✓ | ✓ | ✓ |
| FR-04 | Ticket Status Tracking | ✓ | | | | | | | | | | | ✓ | ✓ | ✓ |
| FR-05 | Service Request Routing | | | | | ✓ | | | | | | ✓ | ✓ | ✓ | |
| FR-06 | Staff Ticket Assignment | ✓ | ✓ | ✓ | | ✓ | | | | ✓ | | | ✓ | ✓ | ✓ |
| FR-07 | Ticket Status Update | ✓ | ✓ | ✓ | | | | | | | ✓ | | ✓ | ✓ | ✓ |
| FR-08 | Ticket Escalation | ✓ | ✓ | ✓ | | ✓ | | | | | ✓ | | ✓ | ✓ | ✓ |
| FR-09 | Priority-Based Queue | | ✓ | ✓ | ✓ | ✓ | | | | | ✓ | | | ✓ | |
| FR-10 | Thread-Safe Concurrency | ✓ | ✓ | ✓ | ✓ | ✓ | | | | | | | | ✓ | |
| FR-11 | Socket Communication | ✓ | ✓ | ✓ | | | | | | | | ✓ | | ✓ | ✓ |
| FR-12 | Ticket Search (TQL) | ✓ | | | | | ✓ | ✓ | ✓ | | | | ✓ | ✓ | ✓ |
| FR-13 | Analytics & Reporting | ✓ | ✓ | ✓ | ✓ | | | | | | | | ✓ | ✓ | ✓ |
| FR-14 | Audit Logging | | | ✓ | | | | | | | | ✓ | ✓ | ✓ | |
| FR-15 | Ticket Resolution | ✓ | ✓ | ✓ | | ✓ | | | | | | ✓ | ✓ | ✓ | ✓ |
| FR-16 | Category Management | ✓ | | | | | | | | ✓ | | ✓ | ✓ | ✓ | ✓ |
| FR-17 | Admin Ticket Override | ✓ | | ✓ | | | | | | ✓ | | | ✓ | ✓ | ✓ |

---

## 3. Non-Functional Requirement → SCD Concept Mapping

| NFR ID | Requirement | Primary SCD Concepts | Implementation Strategy |
|---|---|---|---|
| NFR-01 | Thread Safety | SCD-02, SCD-03, SCD-04, SCD-05 | `PriorityBlockingQueue` (intrinsic), `ConcurrentHashMap` for sessions, `ReentrantReadWriteLock` for analytics cache |
| NFR-02 | Performance | SCD-02, SCD-05, SCD-12 | Thread pool avoids per-request thread creation; `PriorityBlockingQueue.poll()` is O(log n); SQLite WAL mode for concurrent reads |
| NFR-03 | Reliability | SCD-01, SCD-12 | `try-with-resources` on sockets; SQLite transactions with rollback; `ClientHandler` catches all exceptions per-thread |
| NFR-04 | Maintainability | SCD-11, SCD-09 | Repository pattern decouples SQL from business logic; strict layering enforced by package structure |
| NFR-05 | Testability | SCD-13, SCD-12 | In-memory SQLite (`jdbc:sqlite::memory:`) for repository tests; no JavaFX/socket dependency in service classes |
| NFR-06 | Usability | SCD-14 | JavaFX `Task<>` for background socket I/O; `Platform.runLater()` to update UI thread safely |
| NFR-07 | Security | SCD-03, SCD-11 | BCrypt in `PasswordUtil`; session validation on every handler; role check in `requireRole()` |
| NFR-08 | Portability | — | Java 21 + Maven = platform-independent build; `sqlite-jdbc` bundles native SQLite for Windows x64 |
| NFR-09 | Scalability | SCD-02, SCD-05 | Thread pool size configurable in `server.properties`; department queues loaded from DB (not hardcoded) |
| NFR-10 | Data Integrity | SCD-12 | `PRAGMA foreign_keys = ON`; all multi-step writes in explicit SQLite transactions with rollback on failure |

---

## 4. SCD Concept Implementation Detail

### SCD-01 — Java Sockets

| Aspect | Detail |
|---|---|
| **Class** | `HelpdeskServer`, `ClientHandler`, `SocketClient` |
| **Architecture** | Separate Java server process; multiple standalone JavaFX desktop clients |
| **API used** | `java.net.ServerSocket`, `java.net.Socket`, `BufferedReader`, `PrintWriter` |
| **Port** | 9090 (configurable via `server.properties`) |
| **Protocol** | Newline-delimited UTF-8 JSON |
| **Client I/O** | Background `javafx.concurrent.Task` thread; result posted via `Platform.runLater()` |
| **Server I/O** | Each `ClientHandler` reads in a `while(!Thread.currentThread().isInterrupted())` loop |
| **Shutdown** | Graceful shutdown hook closes `ServerSocket` and thread pool; `ClientHandler` detects EOF |
| **Scripts** | Standalone Windows `.bat` run scripts for server and clients |

---

### SCD-02 — Concurrency / Multithreading

| Aspect | Detail |
|---|---|
| **Class** | `HelpdeskServer`, `AuthService` |
| **Thread pool** | `Executors.newFixedThreadPool(poolSize)` where `poolSize` read from `server.properties` (default: 20) |
| **Worker unit** | `ClientHandler implements Runnable` — one per active client connection |
| **Scheduled worker** | `ScheduledExecutorService` (single daemon thread) running session sweeper every 5 minutes |
| **Client-side** | `javafx.concurrent.Task<MessageResponse>` — runs socket I/O off the JavaFX Application Thread |
| **Lifecycle** | Server loop: `socket = serverSocket.accept(); threadPool.submit(new ClientHandler(socket, ...))` |

---

### SCD-03 — Thread Safety

| Shared Resource | Protection Mechanism | Justification |
|---|---|---|
| Session map | `ConcurrentHashMap<String, Session>` | Lock-free for reads; atomic `putIfAbsent` for new sessions |
| Session expiry | `ScheduledExecutorService` (daemon) | Periodically sweeps sessions with `lastAccessedAt` older than 30 minutes |
| Department queues | `PriorityBlockingQueue<Ticket>` | Blocking queue is fully thread-safe; no external lock needed |
| Analytics cache | `ReentrantReadWriteLock` | Multiple readers allowed; single exclusive writer |
| SQLite connection | `synchronized` block or connection-per-thread | SQLite in WAL mode: safe concurrent reads, serialised writes |
| Ticket status transitions | `TicketService.validateTransition()` + DB `UPDATE WHERE status=?` | Optimistic concurrency — DB constraint catches invalid transitions |
| Password hashing | jBCrypt (`PasswordUtil.hash()`, `PasswordUtil.verify()`) | Safe, adaptive salted BCrypt hashing (no plaintext exposure) |

---

### SCD-04 — Locks

| Lock | Class | Usage |
|---|---|---|
| `ReentrantReadWriteLock` | `AnalyticsService` | `readLock()` for cache reads; `writeLock()` for cache invalidation and refresh |
| `ReentrantLock` | `TicketQueueManager` (optional) | Guards multi-step enqueue + DB update atomicity if not covered by `PriorityBlockingQueue` alone |

**Deadlock Prevention**:
- Global lock ordering: analytics cache lock always acquired before database lock (never reversed).
- All locks use `try { lock.lock(); ... } finally { lock.unlock(); }` pattern — no lock leaks.

---

### SCD-05 — Blocking Queues

| Aspect | Detail |
|---|---|
| **Type** | `java.util.concurrent.PriorityBlockingQueue<Ticket>` |
| **One queue per** | Department — `Map<String, PriorityBlockingQueue<Ticket>>` in `TicketQueueManager` |
| **Ordering** | `Ticket implements Comparable<Ticket>` — by priority ordinal, then `createdAt` timestamp |
| **Enqueue** | `queue.put(ticket)` — never blocks (unbounded queue) |
| **Dequeue (claim)** | `queue.poll()` — non-blocking; returns `null` if empty → staff shown "No tickets" message |
| **Dequeue (escalation re-enqueue)** | Ticket removed from queue by `removeIf(t -> t.getId() == ticketId)`, priority bumped, re-enqueued |

---

### SCD-06 — Parser Generators

| Aspect | Detail |
|---|---|
| **Tool** | ANTLR 4.13.1 |
| **Grammar file** | `src/main/antlr4/com/campusflow/tql/TicketQuery.g4` |
| **Generated files** | `TicketQueryLexer.java`, `TicketQueryParser.java`, `TicketQueryVisitor.java`, `TicketQueryBaseVisitor.java` |
| **Maven plugin** | `antlr4-maven-plugin` — runs at `generate-sources` phase; generated code output to `target/generated-sources/antlr4/` |
| **Runtime dependency** | `org.antlr:antlr4-runtime:4.13.1` |

---

### SCD-07 — Formal Grammars

The TQL grammar defines a **context-free grammar** (CFG) with separate lexer and parser rules:

```antlr
grammar TicketQuery;

// ── Parser Rules (CFG production rules) ─────────────
query       : filterExpr (AND filterExpr)* EOF ;
filterExpr  : FIELD COLON value                ;
value       : STRING
            | QUOTED_STRING
            ;

// ── Lexer Rules (regular expressions → tokens) ──────
AND          : 'AND'                           ;
FIELD        : 'status'
             | 'priority'
             | 'category'
             | 'department'
             | 'assigned'
             | 'submitter'
             ;
COLON        : ':'                             ;
STRING       : [a-zA-Z_][a-zA-Z0-9_]*         ;
QUOTED_STRING: '"' (~["\r\n])* '"'            ;
WS           : [ \t\r\n]+ -> skip             ;
```

**Grammar properties**:
- **LL(1) compatible** — ANTLR4 handles LL(*) but TQL is intentionally simple
- **Ambiguity-free** — `AND` keyword is distinct from `STRING` via lexer rule ordering
- **Error recovery** — ANTLR4's default error recovery strategy reports all syntax errors with token positions

---

### SCD-08 — Little Languages

**Language name**: TQL — Ticket Query Language

| Property | Detail |
|---|---|
| **Purpose** | Filter tickets using structured field:value expressions |
| **Target users** | All roles: Students (strictly scoped to their own tickets), Staff (department/assigned scope), and Admins (global) |
| **Authorization** | Enforced strictly on server: `TicketRepository.search()` automatically appends `AND t.submitter_id = ?` for Students |
| **Syntax** | `field:value` pairs joined by `AND`; values optionally quoted |
| **Valid fields** | `status`, `priority`, `category`, `department`, `assigned`, `submitter` |
| **Example programs** | `status:open` / `priority:critical AND department:IT` / `category:"IT Support" AND status:escalated` |
| **Compilation** | Parsed by ANTLR; "compiled" to a parameterised SQL `WHERE` clause |
| **Error handling** | Syntax errors caught at parse time; displayed inline in UI; no server request sent |
| **Why a Little Language?** | A dropdown-based filter UI would require 6 separate controls; TQL lets power users express complex multi-field queries in a single text field — demonstrating that domain-specific languages solve real UX problems |

---

### SCD-09 — OOP Inheritance

```
User (abstract-like base)
 ├── Student      — adds: submitTicket(), getMyTickets()
 ├── StaffMember  — adds: departmentId, getAssignedTickets()
 └── Admin        — adds: manageUsers(), generateReport()
```

**Usage in dispatch**: `ClientHandler.requireRole(user, Role.STAFF)` — checks `user.getRole()` without instanceof cascade.

---

### SCD-10 — OOP Polymorphism

| Polymorphic mechanism | Where | Benefit |
|---|---|---|
| `Ticket implements Comparable<Ticket>` | `PriorityBlockingQueue` ordering | Queue uses natural ordering without external comparator |
| `TicketQueryVisitorImpl extends TicketQueryBaseVisitor` | ANTLR visitor pattern | Grammar-driven dispatch; each `visit*` method handles one grammar rule |
| `TicketStatus.canTransitionTo(next)` | Transition validation | Each status knows its valid successors — Open/Closed Principle |
| Role-based method dispatch | `ClientHandler.requireRole()` | Single code path; role enum checked polymorphically |

---

### SCD-11 — Design Patterns

| Pattern | Implementation | Location |
|---|---|---|
| **Repository** | Encapsulates all SQL; services never write raw JDBC | `*Repository` classes |
| **DTO (Data Transfer Object)** | `TicketSubmitDto`, `UserCreateDto`, `MessageRequest`, `MessageResponse` | `model/protocol/` |
| **Strategy** | `RoutingService` could be replaced with any `RoutingStrategy` interface implementation | `service/RoutingService` |
| **Template Method** | `ClientHandler.dispatch()` — fixed skeleton with per-type hooks | `server/ClientHandler` |
| **Singleton** | `DatabaseManager.getConnection()` returns shared connection | `util/DatabaseManager` |
| **Observer (manual)** | `TicketService` calls `analyticsService.invalidateCache()` after every write | `service/TicketService` |
| **Comparable / Natural Order** | `Ticket.compareTo()` defines priority-then-time ordering | `model/Ticket` |

---

### SCD-12 — JDBC / SQLite

| Aspect | Detail |
|---|---|
| **Driver** | `org.xerial:sqlite-jdbc:3.45.3.0` |
| **Connection URL** | `jdbc:sqlite:campusflow.db` (server) / `jdbc:sqlite::memory:` (tests) |
| **Foreign keys** | `PRAGMA foreign_keys = ON` — executed on every new connection |
| **WAL mode** | `PRAGMA journal_mode = WAL` — allows concurrent reads while writing |
| **Transactions** | `conn.setAutoCommit(false); ... conn.commit(); catch → conn.rollback()` |
| **Prepared statements** | All queries use `PreparedStatement` — no string concatenation, no SQL injection |
| **Schema init** | `DatabaseManager.initialiseSchema()` runs all `CREATE TABLE IF NOT EXISTS` DDL at startup |
| **Seed data** | `DatabaseManager.seedData()` inserts departments, categories, default admin if tables empty |

---

### SCD-13 — JUnit 5 Test Plan

| Test Class | Layer Tested | Key Tests |
|---|---|---|
| `TicketQueueManagerTest` | Service | Enqueue/dequeue ordering by priority; thread-safety with 10 concurrent producers |
| `TicketServiceTest` | Service | Submit, claim, update status, escalate; invalid transitions throw `IllegalStateException` |
| `RoutingServiceTest` | Service | Category-to-department mapping for all 9 seed categories |
| `AuthServiceTest` | Service | Correct credentials return session; wrong credentials return empty; session expiry |
| `TicketRepositoryTest` | Repository | CRUD against in-memory SQLite; `search()` with filter combinations |
| `UserRepositoryTest` | Repository | Save, findByUsername, deactivate against in-memory SQLite |
| `TQLParserTest` | TQL | Valid queries produce correct `TicketFilter`; 5+ invalid queries produce `ParseException` |
| `AnalyticsServiceTest` | Service | Cache hit returns same object; cache miss reloads; `ReadWriteLock` not held after return |
| `TicketStatusTransitionTest` | Model | All valid transitions pass; all invalid transitions throw |
| `PasswordUtilTest` | Util | Hash is not plaintext; correct password matches hash; incorrect password does not |

**Test configuration**:
```xml
<!-- JUnit 5 already in pom.xml -->
<!-- Repository tests use: jdbc:sqlite::memory: (no file I/O, fast) -->
<!-- No server or JavaFX needed for any unit test -->
```

---

### SCD-14 — JavaFX UI

| Aspect | Detail |
|---|---|
| **FXML** | Each screen has a `.fxml` layout file in `src/main/resources/com/campusflow/ui/` |
| **Controller pattern** | Each `*Controller` annotated with `@FXML`; zero business logic |
| **Background I/O** | `javafx.concurrent.Task<MessageResponse>` runs `socketClient.send()` off the Application Thread |
| **Thread-safe UI update** | `task.setOnSucceeded(e -> Platform.runLater(() -> updateUI(task.getValue())))` |
| **Charts** | `AnalyticsDashboardController` uses JavaFX `PieChart`, `BarChart`, `LineChart` |
| **CSS** | Single `styles.css` in `resources/`; role-specific accent colours via CSS classes |

---

## 5. Concept Coverage Summary

| SCD Concept | FRs that exercise it | NFRs that govern it | Status |
|---|---|---|---|
| SCD-01 Sockets | FR-02,03,04,06,07,08,11,13,15,16,17 | NFR-03 | ✅ Fully planned |
| SCD-02 Concurrency | FR-02,03,06,07,08,09,10,11,13,15 | NFR-01, NFR-02, NFR-09 | ✅ Fully planned |
| SCD-03 Thread Safety | FR-02,03,06,07,08,09,10,13,14,15,17 | NFR-01, NFR-07 | ✅ Fully planned |
| SCD-04 Locks | FR-09,10,13 | NFR-01 | ✅ Fully planned |
| SCD-05 Blocking Queues | FR-03,05,06,08,09,10,15 | NFR-01, NFR-02 | ✅ Fully planned |
| SCD-06 Parser Generators | FR-12 | — | ✅ Fully planned |
| SCD-07 Grammars | FR-12 | — | ✅ Fully planned |
| SCD-08 Little Languages | FR-12 | — | ✅ Fully planned |
| SCD-09 OOP Inheritance | FR-01,02,06,16,17 | NFR-04 | ✅ Fully planned |
| SCD-10 OOP Polymorphism | FR-07,08,09,12 | NFR-04 | ✅ Fully planned |
| SCD-11 Design Patterns | FR-01..17 (cross-cutting) | NFR-04 | ✅ Fully planned |
| SCD-12 JDBC/SQLite | FR-01..17 (cross-cutting) | NFR-10 | ✅ Fully planned |
| SCD-13 JUnit 5 | All FRs (validation) | NFR-05 | ✅ Fully planned |
| SCD-14 JavaFX | FR-01..04,06,07,13,16 | NFR-06 | ✅ Fully planned |

**All 14 SCD concepts are covered and grounded in concrete implementation plans.**

---

## Document History

| Version | Date | Author | Notes |
|---|---|---|---|
| 1.0 | 2026-11-19 | Project Team | Initial traceability matrix |
