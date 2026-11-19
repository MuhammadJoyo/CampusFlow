# UML Activity Diagrams
## CampusFlow — Smart Campus Helpdesk & Queue Management System

> **Document ID**: UML-003
> **Version**: 1.0
> **Date**: 2026-11-19

---

## AD-01 — Complete Ticket Lifecycle

This diagram shows the full lifecycle of a ticket from submission through resolution
and closure, including escalation and admin override paths.

```mermaid
flowchart TD
    START(["● Start\nStudent submits complaint"])

    V1{"Client-side\nvalidation\npassed?"}
    SEND["Send SUBMIT_TICKET\nover socket"]
    AUTH1{"Session\nvalid?"}
    SAVE["TicketService:\nSave ticket to DB\n(OPEN status)"]
    ROUTE["RoutingService:\nDetermine department"]
    QUEUE["TicketQueueManager:\nEnqueue in PriorityBlockingQueue\n(ordered by Priority + timestamp)"]
    AUDIT1["AuditLog:\nlog CREATE event"]

    WAIT["Ticket waits\nin priority queue"]

    CLAIM{"Staff claims\nor Admin\nassigns?"}
    CLAIM_S["Staff: CLAIM_TICKET\n(dequeue top ticket)"]
    CLAIM_A["Admin: ASSIGN_TICKET\n(bypass queue)"]
    IN_PROG["Status → IN_PROGRESS\nassigned_staff_id set"]
    AUDIT2["AuditLog:\nlog ASSIGN event"]

    STAFF_WORK["Staff works\non ticket"]
    RESOLVE_Q{"Resolvable\nby staff?"}

    ESC_PATH["Staff: ESCALATE_TICKET\nPriority bumped\nStatus → ESCALATED\nRe-enqueued in dept queue"]
    AUDIT3["AuditLog:\nlog ESCALATE event"]

    RESOLVE["Staff: UPDATE_TICKET_STATUS\nStatus → RESOLVED\nresolved_at = now\nResolution note added"]
    AUDIT4["AuditLog:\nlog RESOLVE event"]
    CACHE_INV["AnalyticsService:\ninvalidateCache()"]

    DEQUEUE["TicketQueueManager:\nRemove from queue"]

    CLOSE_Q{"Admin force-close\nor 7-day auto?"}
    CLOSE["Status → CLOSED\nImmutable"]
    AUDIT5["AuditLog:\nlog CLOSE event"]

    END(["⬤ End\nTicket CLOSED"])

    ERR_V["Show validation\nerror to student"]
    ERR_A["Return\nSESSION_EXPIRED"]
    ERR_DB["Rollback transaction\nReturn SERVER_ERROR"]

    START --> V1
    V1 -- No --> ERR_V --> START
    V1 -- Yes --> SEND --> AUTH1
    AUTH1 -- No --> ERR_A
    AUTH1 -- Yes --> SAVE
    SAVE -- DB error --> ERR_DB
    SAVE -- ok --> ROUTE --> QUEUE --> AUDIT1 --> WAIT

    WAIT --> CLAIM
    CLAIM -- Staff self-claim --> CLAIM_S --> IN_PROG
    CLAIM -- Admin assigns --> CLAIM_A --> IN_PROG
    IN_PROG --> AUDIT2 --> STAFF_WORK --> RESOLVE_Q

    RESOLVE_Q -- No --> ESC_PATH --> AUDIT3 --> WAIT
    RESOLVE_Q -- Yes --> RESOLVE --> DEQUEUE
    RESOLVE --> AUDIT4 --> CACHE_INV
    DEQUEUE --> CLOSE_Q

    CLOSE_Q -- Yes --> CLOSE --> AUDIT5 --> END
    CLOSE_Q -- No, wait --> CLOSE_Q
```

---

## AD-02 — Server Request Processing

This diagram shows how `ClientHandler` processes every incoming TCP message,
covering authentication, role authorisation, dispatch, and error handling.

```mermaid
flowchart TD
    ACCEPT(["● Socket accepted\nClientHandler.run() starts"])

    READ["Read line from BufferedReader\n(blocks until newline received)"]
    EOF{"End of stream /\nclient disconnected?"}
    PARSE["Parse JSON → MessageRequest\n(type, sessionId, payload)"]
    PARSE_ERR{"Parse\nerror?"}

    SESSION["AuthService.validateSession(sessionId)\nConcurrentHashMap.get()"]
    SESS_VALID{"Session\nvalid?"}

    DISPATCH{"Dispatch on\nmessage type"}

    H_LOGIN["handleLogin()\nAuthService.authenticate()"]
    H_SUBMIT["handleSubmitTicket()\nrequireRole(STUDENT)\nTicketService.submitTicket()"]
    H_CLAIM["handleClaimTicket()\nrequireRole(STAFF)\nTicketService.claimNextTicket()"]
    H_UPDATE["handleUpdateStatus()\nrequireRole(STAFF)\nTicketService.updateStatus()"]
    H_ESCALATE["handleEscalate()\nrequireRole(STAFF)\nTicketService.escalateTicket()"]
    H_ASSIGN["handleAssign()\nrequireRole(ADMIN)\nTicketService.assignTicket()"]
    H_USERS["handleGetUsers()\nrequireRole(ADMIN)\nUserManagementService.getAllUsers()"]
    H_CREATE_USER["handleCreateUser()\nrequireRole(ADMIN)\nUserManagementService.createUser()"]
    H_ANALYTICS["handleGetAnalytics()\nrequireRole(ADMIN)\nAnalyticsService.getReport()"]
    H_SEARCH["handleSearch()\nTicketService.searchTickets()"]
    H_UNKNOWN["Return ERROR\nUNKNOWN_TYPE"]

    ROLE_ERR["Return ERROR\nFORBIDDEN"]
    BUILD_RESP["Build MessageResponse\nserialise to JSON"]
    WRITE["writer.println(responseJson)\nFlush to client"]
    CLOSE(["⬤ Close socket\nThread returns to pool"])

    ACCEPT --> READ
    READ --> EOF
    EOF -- Yes --> CLOSE
    EOF -- No --> PARSE
    PARSE --> PARSE_ERR
    PARSE_ERR -- Yes --> H_UNKNOWN --> BUILD_RESP
    PARSE_ERR -- No --> SESSION

    SESSION --> SESS_VALID
    SESS_VALID -- No, and type≠LOGIN --> BUILD_RESP
    SESS_VALID -- No, type=LOGIN --> DISPATCH
    SESS_VALID -- Yes --> DISPATCH

    DISPATCH -- LOGIN --> H_LOGIN
    DISPATCH -- SUBMIT_TICKET --> H_SUBMIT
    DISPATCH -- CLAIM_TICKET --> H_CLAIM
    DISPATCH -- UPDATE_TICKET_STATUS --> H_UPDATE
    DISPATCH -- ESCALATE_TICKET --> H_ESCALATE
    DISPATCH -- ASSIGN_TICKET --> H_ASSIGN
    DISPATCH -- GET_USERS --> H_USERS
    DISPATCH -- CREATE_USER --> H_CREATE_USER
    DISPATCH -- GET_ANALYTICS --> H_ANALYTICS
    DISPATCH -- SEARCH_TICKETS --> H_SEARCH
    DISPATCH -- unknown --> H_UNKNOWN

    H_SUBMIT -- role check fails --> ROLE_ERR
    H_CLAIM -- role check fails --> ROLE_ERR
    H_UPDATE -- role check fails --> ROLE_ERR
    H_ESCALATE -- role check fails --> ROLE_ERR
    H_ASSIGN -- role check fails --> ROLE_ERR
    H_USERS -- role check fails --> ROLE_ERR
    H_CREATE_USER -- role check fails --> ROLE_ERR
    H_ANALYTICS -- role check fails --> ROLE_ERR

    ROLE_ERR --> BUILD_RESP
    H_LOGIN --> BUILD_RESP
    H_SUBMIT --> BUILD_RESP
    H_CLAIM --> BUILD_RESP
    H_UPDATE --> BUILD_RESP
    H_ESCALATE --> BUILD_RESP
    H_ASSIGN --> BUILD_RESP
    H_USERS --> BUILD_RESP
    H_CREATE_USER --> BUILD_RESP
    H_ANALYTICS --> BUILD_RESP
    H_SEARCH --> BUILD_RESP

    BUILD_RESP --> WRITE --> READ
```

---

## AD-03 — TQL Query Processing Pipeline

Shows how a raw TQL string flows through the ANTLR-generated parser into a SQL query.

```mermaid
flowchart TD
    INPUT(["● User types TQL string\ne.g. priority:high AND status:open"])

    LEXER["TicketQueryLexer\nTokenise input stream:\nFIELD COLON STRING AND FIELD COLON STRING"]
    LEX_ERR{"Lexer\nerror?"}

    PARSER["TicketQueryParser\nBuild parse tree:\nquery → filterExpr AND filterExpr"]
    PARSE_ERR{"Parser\nerror?"}

    VISITOR["TicketQueryVisitorImpl\nvisitQuery() ─► visitFilterExpr() x2\nMap field names to TicketFilter fields"]

    FILTER["TicketFilter object\npriority = HIGH\nstatus = OPEN"]

    SCOPE["Apply server-side role authorization:\nSTUDENT: forcefully add WHERE submitter_id=?\nSTAFF: add WHERE department=?\nADMIN: global query access"]

    SQL["Build parameterised SQL:\nSELECT ... FROM tickets t\nJOIN categories c ON t.category_id=c.id\nJOIN departments d ON c.department_id=d.id\nWHERE t.priority='HIGH'\n  AND t.status='OPEN'\n  AND t.submitter_id=5\nORDER BY priority_rank, t.created_at"]

    EXEC["TicketRepository executes\nprepared statement against SQLite"]
    RESULTS["List of Ticket objects\nreturned to client"]

    ERR_LEX["Return ParseException:\nUnrecognised character\nUI shows inline error"]
    ERR_PARSE["Return ParseException:\nSyntax error at token\nUI shows inline error"]

    INPUT --> LEXER
    LEXER --> LEX_ERR
    LEX_ERR -- Yes --> ERR_LEX
    LEX_ERR -- No --> PARSER
    PARSER --> PARSE_ERR
    PARSE_ERR -- Yes --> ERR_PARSE
    PARSE_ERR -- No --> VISITOR --> FILTER --> SCOPE --> SQL --> EXEC --> RESULTS
```

---

## AD-04 — Priority Queue Ordering

Illustrates how `Ticket.compareTo()` governs ordering inside `PriorityBlockingQueue`.

```mermaid
flowchart TD
    TICKET_A["Ticket A arrives\npriority=HIGH\ncreatedAt=10:01"]
    TICKET_B["Ticket B arrives\npriority=CRITICAL\ncreatedAt=10:03"]
    TICKET_C["Ticket C arrives\npriority=HIGH\ncreatedAt=10:00"]

    CMP["PriorityBlockingQueue\ncalls Ticket.compareTo()"]

    RULE1{"Same priority?"}
    RULE2["Compare by priority ordinal\n(CRITICAL=0, HIGH=1,\n MEDIUM=2, LOW=3)"]
    RULE3["Tiebreaker:\nEarlier createdAt wins"]

    ORDER["Queue order:\n1. Ticket B (CRITICAL, 10:03)\n2. Ticket C (HIGH, 10:00)\n3. Ticket A (HIGH, 10:01)"]

    STAFF_CLAIM["Staff calls CLAIM_TICKET\nPriorityBlockingQueue.poll()"]
    GETS["Staff receives: Ticket B (CRITICAL)"]

    TICKET_A --> CMP
    TICKET_B --> CMP
    TICKET_C --> CMP
    CMP --> RULE1
    RULE1 -- No --> RULE2
    RULE1 -- Yes --> RULE3
    RULE2 --> ORDER
    RULE3 --> ORDER
    ORDER --> STAFF_CLAIM --> GETS
```

---

## Document History

| Version | Date | Author | Notes |
|---|---|---|---|
| 1.0 | 2026-11-19 | Project Team | Initial activity diagrams |
