# Software Requirements Specification (SRS)
## CampusFlow — Smart Campus Helpdesk & Queue Management System

> **Document ID**: SRS-001
> **Version**: 1.0
> **Date**: 2026-11-19
> **Status**: Approved for Design Phase

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Overall Description](#2-overall-description)
3. [Functional Requirements](#3-functional-requirements)
4. [Non-Functional Requirements](#4-non-functional-requirements)
5. [System Interfaces](#5-system-interfaces)
6. [Constraints](#6-constraints)
7. [Assumptions & Dependencies](#7-assumptions--dependencies)

---

## 1. Introduction

### 1.1 Purpose

This document specifies all functional and non-functional requirements for CampusFlow,
a Smart Campus Helpdesk and Queue Management System. It serves as the authoritative
reference for system design, implementation, and testing throughout the project.

### 1.2 Scope

CampusFlow is a Java 21 desktop application with a client-server architecture. It
provides:

- A **student-facing client** to submit and track complaints
- A **central helpdesk server** to queue, prioritise, and route tickets
- A **staff interface** to manage and resolve assigned tickets
- An **admin console** to manage users, categories, and view analytics

**In scope**: complaint/ticket lifecycle, user management, priority queuing,
socket communication, analytics, audit logging.

**Out of scope**: course scheduling, room booking, grade management, event
management, mobile/web interfaces, external email/SMS, file attachments.

### 1.3 Definitions, Acronyms & Abbreviations

| Term | Definition |
|---|---|
| **Ticket** | A single complaint or service request submitted by a Student |
| **TQL** | Ticket Query Language — ANTLR-parsed DSL for filtering tickets |
| **FR** | Functional Requirement |
| **NFR** | Non-Functional Requirement |
| **SCD** | Software Construction and Development (course context) |
| **LAN** | Local Area Network — the network over which sockets operate |
| **Priority** | Ticket urgency: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| **Status** | Ticket lifecycle state: `OPEN`, `IN_PROGRESS`, `ESCALATED`, `RESOLVED`, `CLOSED` |
| **Session** | Server-side token issued after successful login |
| **ADR** | Architectural Decision Record |

### 1.4 References

| Document | Location |
|---|---|
| Project Overview | `docs/requirements/PROJECT_OVERVIEW.md` |
| System Architecture | `docs/architecture/SYSTEM_ARCHITECTURE.md` |
| Database Schema | `docs/architecture/DATABASE_SCHEMA.md` |
| Use Cases | `docs/requirements/USE_CASES.md` |
| Traceability Matrix | `docs/requirements/TRACEABILITY_MATRIX.md` |

---

## 2. Overall Description

### 2.1 Product Perspective

CampusFlow replaces ad-hoc campus communication channels (email, WhatsApp, walk-in
desks) with a structured, priority-aware helpdesk. The system consists of two
processes communicating over TCP sockets:

```
[Student/Staff/Admin JavaFX Client] ←── TCP Socket ──→ [CampusFlow Server]
                                                              │
                                                        [SQLite Database]
```

### 2.2 Product Functions (Summary)

| ID | Function |
|---|---|
| FR-01 | User account creation (admin-initiated) |
| FR-02 | User authentication (login / logout) |
| FR-03 | Complaint / ticket submission |
| FR-04 | Ticket status tracking |
| FR-05 | Automatic service request routing by department |
| FR-06 | Staff ticket assignment |
| FR-07 | Ticket status updates by staff |
| FR-08 | Ticket escalation |
| FR-09 | Priority-based queue management |
| FR-10 | Thread-safe concurrent request handling |
| FR-11 | Socket-based client-server communication |
| FR-12 | Ticket search using TQL (ANTLR DSL) |
| FR-13 | Analytics and reporting |
| FR-14 | Audit logging |
| FR-15 | Ticket resolution and closure |
| FR-16 | Admin category and department management |
| FR-17 | Staff ticket escalation path |

### 2.3 User Classes and Characteristics

| Role | Description | Expertise |
|---|---|---|
| **Student** | Enrolled student submitting campus service requests | Low technical — UI must be simple |
| **Staff Member** | IT technician, maintenance worker, or academic admin resolving tickets | Medium — familiar with ticket systems |
| **Administrator** | System owner managing users, categories, and reports | High — manages platform configuration |

### 2.4 Operating Environment

- **OS**: Windows 10/11
- **Runtime**: Java 21 JDK
- **Network**: LAN (`localhost` or LAN IP for socket connections)
- **Database**: SQLite embedded on the server machine
- **Build**: Apache Maven 3.10+

---

## 3. Functional Requirements

> Format: **ID | Name | Description | Priority | Actors | Related UC**

---

### FR-01 — User Account Creation

| Field | Detail |
|---|---|
| **ID** | FR-01 |
| **Name** | User Account Creation |
| **Description** | An Administrator can create user accounts specifying username, password, full name, email, and role (Student / Staff / Admin). Passwords are stored as BCrypt hashes. New accounts are `active` by default. |
| **Priority** | High |
| **Actor** | Administrator |
| **Precondition** | Admin is authenticated |
| **Postcondition** | New user record persisted in `users` table; audit log entry created |
| **Related UC** | UC-10 |

---

### FR-02 — User Authentication

| Field | Detail |
|---|---|
| **ID** | FR-02 |
| **Name** | User Authentication & Session Lifecycle |
| **Description** | Any user can log in with username and password. The server validates credentials against the hashed password using jBCrypt (`BCrypt.checkpw()`). Upon successful authentication, a session token (UUID) is created and stored in a thread-safe `ConcurrentHashMap` with a `lastAccessedAt` timestamp. The server enforces a fixed 30-minute inactivity timeout (configured via `server.session.timeout.minutes` in `server.properties`). A daemon `ScheduledExecutorService` runs periodically (every 5 minutes) to sweep expired sessions. The client includes the session token in all subsequent requests. |
| **Priority** | High |
| **Actor** | Student, Staff, Admin |
| **Precondition** | User account exists and is active |
| **Postcondition** | Session token issued and stored server-side with active timestamp |
| **Related UC** | UC-01 |

---

### FR-03 — Complaint Submission

| Field | Detail |
|---|---|
| **ID** | FR-03 |
| **Name** | Complaint / Ticket Submission |
| **Description** | A Student submits a ticket providing: title (max 150 chars), description (max 2000 chars), category (selected from active categories), and priority (LOW / MEDIUM / HIGH / CRITICAL). The server assigns a unique ticket ID, persists the ticket, routes it to the appropriate department queue, and returns the ticket ID to the client. |
| **Priority** | High |
| **Actor** | Student |
| **Precondition** | Student is authenticated; at least one active category exists |
| **Postcondition** | Ticket in `OPEN` status persisted; enqueued in department's `PriorityBlockingQueue` |
| **Related UC** | UC-02 |

---

### FR-04 — Ticket Status Tracking

| Field | Detail |
|---|---|
| **ID** | FR-04 |
| **Name** | Ticket Status Tracking |
| **Description** | A Student can view all their submitted tickets with current status, priority, category, assigned staff (if any), and all timeline notes. A Staff member can view all tickets assigned to them. |
| **Priority** | High |
| **Actor** | Student, Staff |
| **Precondition** | User is authenticated |
| **Postcondition** | Read-only — no state change |
| **Related UC** | UC-03 |

---

### FR-05 — Automatic Service Request Routing

| Field | Detail |
|---|---|
| **ID** | FR-05 |
| **Name** | Automatic Service Request Routing |
| **Description** | On ticket submission, the `RoutingService` determines the target department from the ticket's category. Each department has a dedicated `PriorityBlockingQueue`. Routing is automatic and requires no manual admin action. |
| **Priority** | High |
| **Actor** | System (automated) |
| **Precondition** | Ticket submitted with a valid category |
| **Postcondition** | Ticket enqueued in the correct department queue |
| **Related UC** | UC-02 |

---

### FR-06 — Staff Ticket Assignment

| Field | Detail |
|---|---|
| **ID** | FR-06 |
| **Name** | Staff Ticket Assignment |
| **Description** | An Administrator can manually assign an `OPEN` ticket to a specific Staff member within the ticket's department. The system also supports self-assignment: a Staff member may claim the top ticket from their department queue. On assignment, ticket status changes to `IN_PROGRESS`. |
| **Priority** | High |
| **Actor** | Administrator, Staff |
| **Precondition** | Ticket is in `OPEN` status; staff member is active and in the correct department |
| **Postcondition** | Ticket `assigned_staff_id` updated; status set to `IN_PROGRESS`; audit log entry created |
| **Related UC** | UC-05, UC-11 |

---

### FR-07 — Ticket Status Update

| Field | Detail |
|---|---|
| **ID** | FR-07 |
| **Name** | Ticket Status Update |
| **Description** | A Staff member can update the status of a ticket assigned to them. Allowed transitions: `IN_PROGRESS` → `ESCALATED`, `IN_PROGRESS` → `RESOLVED`. Each update requires a note (min 10 chars) explaining the action. The note is persisted in `ticket_notes`. |
| **Priority** | High |
| **Actor** | Staff |
| **Precondition** | Ticket is assigned to the requesting staff member |
| **Postcondition** | Ticket status updated; note persisted; audit log entry created |
| **Related UC** | UC-06 |

---

### FR-08 — Ticket Escalation

| Field | Detail |
|---|---|
| **ID** | FR-08 |
| **Name** | Ticket Escalation |
| **Description** | A Staff member may escalate a ticket they cannot resolve. Escalation sets status to `ESCALATED` and re-enqueues the ticket in the department queue with an effective priority bump (e.g., HIGH → CRITICAL). An Administrator can then reassign escalated tickets. |
| **Priority** | Medium |
| **Actor** | Staff, Administrator |
| **Precondition** | Ticket is `IN_PROGRESS` and assigned to the requesting staff member |
| **Postcondition** | Status set to `ESCALATED`; re-enqueued with elevated priority; audit log entry created |
| **Related UC** | UC-07 |

---

### FR-09 — Priority-Based Queue Management

| Field | Detail |
|---|---|
| **ID** | FR-09 |
| **Name** | Priority-Based Queue Management |
| **Description** | The server maintains one `PriorityBlockingQueue<Ticket>` per department (IT, Maintenance, Academic). Tickets implement `Comparable<Ticket>`: ordering is by priority (CRITICAL > HIGH > MEDIUM > LOW), with timestamp as tiebreaker (earlier first). Staff claiming tickets always receive the highest-priority available ticket. |
| **Priority** | High |
| **Actor** | System (automated) |
| **Precondition** | Server is running; department queues initialised |
| **Postcondition** | Queue ordering maintained after every enqueue / dequeue |
| **Related UC** | UC-02, UC-05 |

---

### FR-10 — Thread-Safe Concurrent Request Handling

| Field | Detail |
|---|---|
| **ID** | FR-10 |
| **Name** | Thread-Safe Concurrent Request Handling |
| **Description** | The `HelpdeskServer` operates as a standalone Java process accepting client connections via a configurable thread pool (`Executors.newFixedThreadPool(poolSize)` configured in `server.properties`, default 20). Each `ClientHandler` runs in its own worker thread. Shared resources are thread-safe: `PriorityBlockingQueue` per department; `AuthService` session map uses `ConcurrentHashMap` with periodic cleanup via `ScheduledExecutorService`; `AnalyticsService` cache uses `ReentrantReadWriteLock`; database access is synchronised per connection. |
| **Priority** | High |
| **Actor** | System (automated) |
| **Precondition** | Server is running |
| **Postcondition** | No data corruption under concurrent load |
| **Related UC** | — (cross-cutting) |

---

### FR-11 — Socket-Based Client-Server Communication

| Field | Detail |
|---|---|
| **ID** | FR-11 |
| **Name** | Socket-Based Client-Server Communication |
| **Description** | The server runs as a separate Java process. Client applications (Student, Staff, and Admin JavaFX desktops) communicate with the server over TCP using `java.net.Socket` / `java.net.ServerSocket`. Server port, thread pool size, and session timeout are loaded from `server.properties` (default port: 9090). Messages are UTF-8 JSON strings delimited by newline (`\n`). Each request has a `type` field, an optional `sessionId`, and a `payload` object. The server responds with `status` (`OK` / `ERROR`), optional `errorCode`, and `data`. Graceful shutdown and client disconnection handling are supported. |
| **Priority** | High |
| **Actor** | System (automated) |
| **Precondition** | Server process is running and listening on the configured port (default: 9090) |
| **Postcondition** | Request processed; response delivered to client |
| **Related UC** | UC-01 through UC-15 |

**Message Protocol (examples)**:
```json
// Request
{ "type": "SUBMIT_TICKET", "sessionId": "uuid", "payload": { "title": "...", "description": "...", "categoryId": 3, "priority": "HIGH" } }

// Response
{ "status": "OK", "data": { "ticketId": 42, "message": "Ticket submitted successfully" } }
```

**Defined Message Types**:

| Type | Actor | Direction |
|---|---|---|
| `LOGIN` | All | Client → Server |
| `LOGOUT` | All | Client → Server |
| `SUBMIT_TICKET` | Student | Client → Server |
| `GET_MY_TICKETS` | Student | Client → Server |
| `GET_TICKET` | All | Client → Server |
| `SEARCH_TICKETS` | All | Client → Server |
| `CLAIM_TICKET` | Staff | Client → Server |
| `UPDATE_TICKET_STATUS` | Staff | Client → Server |
| `ESCALATE_TICKET` | Staff | Client → Server |
| `GET_ASSIGNED_TICKETS` | Staff | Client → Server |
| `ASSIGN_TICKET` | Admin | Client → Server |
| `GET_ALL_TICKETS` | Admin | Client → Server |
| `CREATE_USER` | Admin | Client → Server |
| `DEACTIVATE_USER` | Admin | Client → Server |
| `GET_USERS` | Admin | Client → Server |
| `CREATE_CATEGORY` | Admin | Client → Server |
| `GET_CATEGORIES` | All | Client → Server |
| `GET_ANALYTICS` | Admin | Client → Server |

---

### FR-12 — Ticket Search Using TQL

| Field | Detail |
|---|---|
| **ID** | FR-12 |
| **Name** | Ticket Search (Ticket Query Language) |
| **Description** | Users of all roles (Student, Staff, Admin) can search tickets using TQL — a domain-specific language parsed by an ANTLR4-generated parser. TQL supports field:value filters combined with `AND`. Valid fields: `status`, `priority`, `category`, `department`, `assigned`, `submitter`. Crucially, authorization and ownership are enforced strictly on the server side in `TicketRepository.search()`: Students can only search and view their own tickets (server forcefully injects `WHERE submitter_id = ?`); Staff are scoped to their department or assigned tickets; Admin can search across all tickets. Client-side attempts by Students to query other users' tickets are overridden and rejected by server authorization. |
| **Priority** | Medium |
| **Actor** | Student (own tickets), Staff (department/assigned tickets), Admin (all tickets) |
| **Precondition** | User is authenticated |
| **Postcondition** | Filtered list of tickets returned compliant with server-side authorization |
| **Related UC** | UC-08 |

**TQL Examples**:
```
status:open
priority:high status:open
category:"IT Support" AND priority:critical
department:Maintenance AND status:escalated
```

---

### FR-13 — Analytics and Reporting

| Field | Detail |
|---|---|
| **ID** | FR-13 |
| **Name** | Analytics and Reporting |
| **Description** | The Admin dashboard displays: (1) total tickets by status, (2) ticket volume by category, (3) average resolution time per department, (4) open tickets per staff member, (5) tickets opened vs. resolved over the last 30 days. Analytics results are cached with a `ReentrantReadWriteLock`; cache is invalidated on any ticket status change. |
| **Priority** | Medium |
| **Actor** | Administrator |
| **Precondition** | Admin is authenticated; ticket data exists |
| **Postcondition** | Analytics data returned; read-lock released |
| **Related UC** | UC-14 |

---

### FR-14 — Audit Logging

| Field | Detail |
|---|---|
| **ID** | FR-14 |
| **Name** | Audit Logging |
| **Description** | Every significant action is recorded in the `audit_log` table: ticket creation, status change, assignment, escalation, resolution, user creation/deactivation, category creation. Each log entry records actor ID, action type, affected entity type and ID, timestamp, and a details string. |
| **Priority** | Medium |
| **Actor** | System (automated) |
| **Precondition** | Any state-changing operation completes successfully |
| **Postcondition** | Immutable audit record persisted |
| **Related UC** | — (cross-cutting) |

---

### FR-15 — Ticket Resolution and Closure

| Field | Detail |
|---|---|
| **ID** | FR-15 |
| **Name** | Ticket Resolution and Closure |
| **Description** | A Staff member resolves a ticket by setting status to `RESOLVED` with a mandatory resolution note. After 7 days without student reopen request, the system (or Admin) may `CLOSE` the ticket. `CLOSED` tickets are immutable. `resolved_at` timestamp is recorded. |
| **Priority** | High |
| **Actor** | Staff, Administrator |
| **Precondition** | Ticket is in `IN_PROGRESS` status |
| **Postcondition** | Status set to `RESOLVED`; `resolved_at` populated; dequeued from department queue; audit log entry created |
| **Related UC** | UC-06 |

---

### FR-16 — Admin Category and Department Management

| Field | Detail |
|---|---|
| **ID** | FR-16 |
| **Name** | Admin Category and Department Management |
| **Description** | An Administrator can create, rename, and deactivate complaint categories. Each category belongs to exactly one department (IT, Maintenance, Academic). Deactivating a category prevents new tickets from using it but does not affect existing tickets. |
| **Priority** | Medium |
| **Actor** | Administrator |
| **Precondition** | Admin is authenticated |
| **Postcondition** | Category/department table updated; audit log entry created |
| **Related UC** | UC-12 |

---

### FR-17 — Admin Ticket Override

| Field | Detail |
|---|---|
| **ID** | FR-17 |
| **Name** | Admin Ticket Override |
| **Description** | An Administrator can override ticket priority, force-reassign any ticket to any staff member regardless of department, and manually close tickets. Admin actions bypass the normal workflow constraints but always create audit log entries. |
| **Priority** | Low |
| **Actor** | Administrator |
| **Precondition** | Admin is authenticated; ticket exists |
| **Postcondition** | Ticket updated per admin override; audit log entry created |
| **Related UC** | UC-13 |

---

## 4. Non-Functional Requirements

---

### NFR-01 — Thread Safety

| Field | Detail |
|---|---|
| **ID** | NFR-01 |
| **Name** | Thread Safety |
| **Description** | The server must handle ≥ 20 simultaneous client connections without race conditions, deadlocks, or data corruption. All shared mutable state must use appropriate Java concurrency primitives. |
| **Mechanism** | `PriorityBlockingQueue`, `ConcurrentHashMap`, `ReentrantReadWriteLock`, `synchronized` database access |
| **Measure** | Zero data corruption observed under 20-thread concurrent test |

---

### NFR-02 — Performance

| Field | Detail |
|---|---|
| **ID** | NFR-02 |
| **Name** | Performance |
| **Description** | Ticket submission must be acknowledged within 1 second under normal load (≤ 10 concurrent submissions). UI interactions must respond within 500 ms. Analytics queries must complete within 3 seconds. |
| **Measure** | Measured via JUnit 5 timed tests |

---

### NFR-03 — Reliability

| Field | Detail |
|---|---|
| **ID** | NFR-03 |
| **Name** | Reliability |
| **Description** | No ticket data may be lost due to unhandled exceptions. Server must recover gracefully from client disconnection without crashing or corrupting the queue state. All database writes use SQLite transactions. |
| **Measure** | Server uptime during test suite execution; zero `NullPointerException` propagation to client |

---

### NFR-04 — Maintainability

| Field | Detail |
|---|---|
| **ID** | NFR-04 |
| **Name** | Maintainability |
| **Description** | Adding a new department or complaint category must require zero source code changes — data-driven via admin UI. Adding a new message type to the protocol must require changes only in `ClientHandler` and the relevant service, not in framework classes. |
| **Measure** | Code review; cyclomatic complexity ≤ 10 per method |

---

### NFR-05 — Testability

| Field | Detail |
|---|---|
| **ID** | NFR-05 |
| **Name** | Testability |
| **Description** | Business logic classes (`TicketService`, `RoutingService`, `QueueManager`, `TQLParser`) must have zero JavaFX or socket dependencies so they can be tested with JUnit 5 without a running server or UI. Repository classes must be testable against an in-memory SQLite instance (`jdbc:sqlite::memory:`). |
| **Measure** | ≥ 80% statement coverage on service and repository layers |

---

### NFR-06 — Usability

| Field | Detail |
|---|---|
| **ID** | NFR-06 |
| **Name** | Usability |
| **Description** | A Student must be able to submit a complaint within 3 UI interactions from the main student dashboard. Error messages must be human-readable (not stack traces). |
| **Measure** | User walkthrough; ≤ 3 clicks from dashboard to submission confirmation |

---

### NFR-07 — Security

| Field | Detail |
|---|---|
| **ID** | NFR-07 |
| **Name** | Security |
| **Description** | Passwords must be stored as BCrypt hashes (never plaintext). All server-side operations must validate the session token before processing. Students may only access their own tickets. Staff may only update tickets assigned to them. |
| **Measure** | Security review; no plaintext passwords in database or logs |

---

### NFR-08 — Portability

| Field | Detail |
|---|---|
| **ID** | NFR-08 |
| **Name** | Portability |
| **Description** | The application must run on Windows 10/11 with Java 21 installed. The Maven build must succeed with `mvn compile` on a clean machine with no additional configuration beyond setting `JAVA_HOME`. |
| **Measure** | Successful build on clean VM |

---

### NFR-09 — Scalability

| Field | Detail |
|---|---|
| **ID** | NFR-09 |
| **Name** | Scalability |
| **Description** | Thread pool size must be configurable via `server.properties`. Queue capacity must be effectively unbounded (limited only by JVM heap). The design must not hardcode department names — departments are loaded from the database at startup. |
| **Measure** | Configuration file review; no hardcoded department strings in business logic |

---

### NFR-10 — Data Integrity

| Field | Detail |
|---|---|
| **ID** | NFR-10 |
| **Name** | Data Integrity |
| **Description** | All multi-step database operations (e.g., insert ticket + enqueue + audit log) must be wrapped in a single SQLite transaction. Foreign key constraints must be enabled (`PRAGMA foreign_keys = ON`). |
| **Measure** | SQLite schema review; no orphaned records in test suite |

---

## 5. System Interfaces

### 5.1 User Interfaces

- **Login Screen** — username/password fields, role-independent
- **Student Dashboard** — submit complaint button, my tickets list, ticket detail view
- **Staff Dashboard** — assigned tickets queue, ticket detail with status update panel
- **Admin Dashboard** — user management, category management, all-tickets view, analytics panel

All screens implemented in JavaFX with FXML layout files and CSS styling.

### 5.2 Communication Interface

- **Protocol**: TCP, `java.net.Socket` / `java.net.ServerSocket`
- **Port**: 9090 (configurable in `server.properties`)
- **Message format**: Newline-delimited UTF-8 JSON
- **Serialisation**: Manual JSON string building (Phase 2); Gson library may be added in Phase 3

### 5.3 Software Interfaces

| Component | Interface |
|---|---|
| SQLite | `org.xerial:sqlite-jdbc` JDBC driver |
| Password Hashing | `org.mindrot:jbcrypt` (0.4) BCrypt hashing |
| Logging | SLF4J 2 API + Logback Classic runtime |
| ANTLR | `org.antlr:antlr4-runtime` (to be added to pom.xml in implementation phase) |
| Testing | JUnit 5 Jupiter (`junit-jupiter:5.10.2`) |

---

## 6. Constraints

| Constraint | Detail |
|---|---|
| Language | Java 21 only |
| UI | JavaFX 21 only (no Swing, no web) |
| Database | SQLite only — no MySQL, PostgreSQL or H2 |
| Network | Local TCP sockets only — no HTTP/REST, no WebSockets |
| Build | Maven only — no Gradle |
| Testing | JUnit 5 only — no TestNG |

---

## 7. Assumptions & Dependencies

- Each user has exactly one role; roles cannot change without admin intervention
- The server process and database run on the same physical machine
- Only one administrator account exists initially (seeded at first run)
- The LAN environment is trusted — no TLS/SSL required for this academic context
- Up to three departments exist: **IT**, **Maintenance**, **Academic** — extensible via admin UI

---

## Document History

| Version | Date | Author | Notes |
|---|---|---|---|
| 1.0 | 2026-11-19 | Project Team | Initial SRS — approved for design phase |
