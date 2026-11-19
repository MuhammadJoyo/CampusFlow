# UML Sequence Diagrams
## CampusFlow — Smart Campus Helpdesk & Queue Management System

> **Document ID**: UML-002
> **Version**: 1.0
> **Date**: 2026-11-19

---

## Overview

Four sequence diagrams are provided covering the most architecturally significant
interactions in CampusFlow:

| ID | Diagram | Key SCD Concepts |
|---|---|---|
| SD-01 | User Login | Socket communication, session management, `ConcurrentHashMap` |
| SD-02 | Complaint Submission | Socket, transaction, `PriorityBlockingQueue`, routing, audit log |
| SD-03 | Staff Ticket Resolution | Socket, concurrent state update, audit log, cache invalidation |
| SD-04 | Ticket Search with TQL | ANTLR parsing, socket, parameterised SQL |

---

## SD-01 — User Login

```mermaid
sequenceDiagram
    actor U as User
    participant UI as LoginController
    participant SC as SocketClient
    participant CH as ClientHandler
    participant AS as AuthService
    participant UR as UserRepository
    participant DB as SQLite

    U->>UI: Enter username + password, click Login
    UI->>UI: Validate fields not empty
    UI->>SC: send(MessageRequest LOGIN)
    SC->>CH: TCP — JSON line
    note over CH: Running in ThreadPool thread
    CH->>CH: parse MessageRequest
    CH->>AS: authenticate(username, password)
    AS->>UR: findByUsername(username)
    UR->>DB: SELECT * FROM users WHERE username=?
    DB-->>UR: ResultSet row
    UR-->>AS: Optional[User]
    AS->>AS: PasswordUtil.verify(password, hash) [jBCrypt]
    alt credentials valid and user active
        AS->>AS: UUID sessionId = UUID.randomUUID()
        AS->>AS: sessions.put(sessionId, new Session(..., lastAccessedAt=now))
        note over AS: ConcurrentHashMap — thread-safe put
        AS-->>CH: Optional[sessionId]
        CH->>CH: build success MessageResponse
        CH-->>SC: TCP — JSON line OK
        SC-->>UI: MessageResponse(OK, sessionId, role, fullName)
        UI->>UI: Store sessionId in memory
        UI->>UI: Navigate to role dashboard
        UI-->>U: Dashboard shown
    else invalid credentials or inactive
        AS-->>CH: Optional.empty()
        CH-->>SC: TCP — JSON line ERROR AUTH_FAILED
        SC-->>UI: MessageResponse(ERROR, AUTH_FAILED)
        UI-->>U: Show error message
    end
```

---

## SD-02 — Complaint Submission

```mermaid
sequenceDiagram
    actor S as Student
    participant UI as SubmitTicketController
    participant SC as SocketClient
    participant CH as ClientHandler
    participant AS as AuthService
    participant TS as TicketService
    participant RS as RoutingService
    participant QM as TicketQueueManager
    participant TR as TicketRepository
    participant AL as AuditLogRepository
    participant DB as SQLite

    S->>UI: Fill form (title, desc, category, priority) and click Submit
    UI->>UI: Client-side validation
    note over UI: title 1-150 chars, desc 1-2000 chars
    UI->>SC: send(SUBMIT_TICKET, payload)
    SC->>CH: TCP — JSON line
    CH->>AS: validateSession(sessionId)
    AS->>AS: sessions.get(sessionId)
    note over AS: ConcurrentHashMap.get — thread-safe read
    AS-->>CH: User(id=5, role=STUDENT)
    CH->>TS: submitTicket(dto, submitterId=5)

    rect rgb(240, 248, 255)
        note over TS,DB: SQLite transaction begins
        TS->>TR: save(ticket)
        TR->>DB: INSERT INTO tickets (...)
        DB-->>TR: generated ticketId=42
        TR-->>TS: Ticket(id=42, status=OPEN)

        TS->>RS: route(ticket)
        RS->>RS: getDepartmentForCategory(categoryId)
        note over RS: Category lookup — cached from startup
        RS-->>TS: department = "IT"

        TS->>QM: enqueue(ticket, "IT")
        QM->>QM: departmentQueues.get("IT").put(ticket)
        note over QM: PriorityBlockingQueue.put() — thread-safe
        QM-->>TS: queued

        TS->>AL: log(actorId=5, CREATE, TICKET, 42, details)
        AL->>DB: INSERT INTO audit_log (...)
        DB-->>AL: ok
        note over TS,DB: SQLite transaction commits
    end

    TS-->>CH: Ticket(id=42)
    CH->>CH: build success MessageResponse
    CH-->>SC: TCP — JSON line OK
    SC-->>UI: MessageResponse(OK, ticketId=42)
    UI-->>S: Show "Ticket #42 submitted successfully"
```

---

## SD-03 — Staff Ticket Resolution

```mermaid
sequenceDiagram
    actor ST as Staff Member
    participant UI as TicketDetailController
    participant SC as SocketClient
    participant CH as ClientHandler
    participant AS as AuthService
    participant TS as TicketService
    participant QM as TicketQueueManager
    participant TR as TicketRepository
    participant AL as AuditLogRepository
    participant ANS as AnalyticsService
    participant DB as SQLite

    ST->>UI: Open ticket, click Resolve, enter resolution note
    UI->>UI: Validate note length >= 10 chars
    UI->>SC: send(UPDATE_TICKET_STATUS, {ticketId:42, newStatus:RESOLVED, note})
    SC->>CH: TCP — JSON line
    CH->>AS: validateSession(sessionId)
    AS-->>CH: User(id=7, role=STAFF)
    CH->>TS: updateStatus(42, RESOLVED, note, actorId=7)

    TS->>TR: findById(42)
    TR->>DB: SELECT * FROM tickets WHERE id=42
    DB-->>TR: Ticket(id=42, status=IN_PROGRESS, assignedStaffId=7)
    TR-->>TS: Optional[Ticket]

    TS->>TS: validateTransition(IN_PROGRESS → RESOLVED) ✓
    TS->>TS: validateOwnership(assigned=7, actor=7) ✓

    rect rgb(240, 255, 240)
        note over TS,DB: SQLite transaction begins
        TS->>TR: update(ticket.status=RESOLVED, resolvedAt=now)
        TR->>DB: UPDATE tickets SET status='RESOLVED', resolved_at=? WHERE id=42
        DB-->>TR: ok

        TS->>TR: saveNote(TicketNote(ticketId=42, authorId=7, note))
        TR->>DB: INSERT INTO ticket_notes (...)
        DB-->>TR: ok

        TS->>AL: log(7, RESOLVE, TICKET, 42, details)
        AL->>DB: INSERT INTO audit_log (...)
        DB-->>AL: ok
        note over TS,DB: SQLite transaction commits
    end

    TS->>QM: remove(ticketId=42, "IT")
    note over QM: removeIf — PriorityBlockingQueue is thread-safe
    QM-->>TS: removed

    TS->>ANS: invalidateCache()
    note over ANS: Write lock acquired → cache expiry reset
    ANS-->>TS: cache invalidated

    TS-->>CH: success
    CH-->>SC: TCP — JSON line OK
    SC-->>UI: MessageResponse(OK)
    UI-->>ST: Show "Ticket #42 resolved"
    UI->>UI: Refresh assigned ticket list
```

---

## SD-04 — Ticket Search with TQL

```mermaid
sequenceDiagram
    actor A as Admin
    participant UI as AdminDashboardController
    participant TQL as TicketQueryVisitorImpl
    participant SC as SocketClient
    participant CH as ClientHandler
    participant AS as AuthService
    participant TS as TicketService
    participant TR as TicketRepository
    participant DB as SQLite

    A->>UI: Type TQL query: "priority:high AND status:open"
    UI->>UI: Pass raw query to TQL module
    UI->>TQL: parse("priority:high AND status:open")
    note over TQL: ANTLR Lexer → tokens → Parser → parse tree → Visitor
    TQL->>TQL: TicketQueryLexer tokenises input
    TQL->>TQL: TicketQueryParser builds parse tree
    TQL->>TQL: visitQuery() → visitFilterExpr() x2
    TQL-->>UI: TicketFilter(priority=HIGH, status=OPEN)

    alt parse error (invalid TQL syntax)
        TQL-->>UI: ParseException
        UI-->>A: Show syntax error inline — no server request
    else parse success
        UI->>SC: send(SEARCH_TICKETS, {filter serialised as JSON})
        SC->>CH: TCP — JSON line
        CH->>AS: validateSession(sessionId)
        AS-->>CH: User(id=1, role=ADMIN)
        CH->>TS: searchTickets(filter, ADMIN, userId=1)
        TS->>TR: search(filter, ADMIN, 1)
        TR->>TR: build SQL WHERE clause from filter
        note over TR: WHERE priority='HIGH' AND status='OPEN'
        TR->>DB: SELECT t.*, u.full_name FROM tickets t JOIN users u ...
        DB-->>TR: ResultSet
        TR-->>TS: List[Ticket] (sorted by priority, created_at)
        TS-->>CH: List[Ticket]
        CH-->>SC: TCP — JSON line OK with ticket array
        SC-->>UI: MessageResponse(OK, tickets=[...])
        UI-->>A: Render filtered ticket table
    end
```

---

## Key Concurrency Interactions Highlighted

### Thread Safety in SD-02 (Complaint Submission)

Multiple students can submit simultaneously. Each `ClientHandler` runs in its own
thread from the fixed thread pool:

```
Thread-1: ClientHandler → TicketService.submitTicket() → PriorityBlockingQueue.put(ticket1)
Thread-2: ClientHandler → TicketService.submitTicket() → PriorityBlockingQueue.put(ticket2)
Thread-3: ClientHandler → TicketService.submitTicket() → PriorityBlockingQueue.put(ticket3)
```

`PriorityBlockingQueue.put()` is internally `synchronized` — no external lock
required. The SQLite `TicketRepository.save()` acquires a per-connection lock,
ensuring sequential database writes while allowing concurrent queue operations.

### ReadWriteLock in SD-03 (Analytics Cache Invalidation)

```
Resolution thread:   AnalyticsService.invalidateCache()
                       └── cacheLock.writeLock().lock()
                       └── cachedReport = null; cacheExpiry = now
                       └── cacheLock.writeLock().unlock()

Analytics read threads (concurrent, non-blocking when cache is valid):
                       └── cacheLock.readLock().lock()
                       └── return cachedReport
                       └── cacheLock.readLock().unlock()
```

### ScheduledExecutorService in Session Cleanup (SCD-02, SCD-03)

```
Session Cleaner Daemon Thread (ScheduledExecutorService, every 5 minutes):
  └── AuthService.cleanExpiredSessions()
        └── iterates ConcurrentHashMap<String, Session>
        └── if session.isExpired(30):
              sessions.remove(sessionId)
```

### Server-Side Ownership Enforcement in SD-04 (TQL Search)

When any user issues `SEARCH_TICKETS`, the server guarantees authorization regardless of client parameters:
- **Student**: `TicketRepository.search(filter, Role.STUDENT, studentId)` forcefully injects `AND t.submitter_id = ?` preventing exposure of any other student's complaints.
- **Staff**: Scoped to tickets belonging to the staff member's department or assigned tickets.
- **Admin**: Full visibility across all departments and tickets.

---

## Document History

| Version | Date | Author | Notes |
|---|---|---|---|
| 1.0 | 2026-11-19 | Project Team | Initial sequence diagrams |
