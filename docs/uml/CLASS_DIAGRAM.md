# UML Class Diagram
## CampusFlow — Smart Campus Helpdesk & Queue Management System

> **Document ID**: UML-001
> **Version**: 1.0
> **Date**: 2026-11-19

---

## 1. Overview

The class diagram covers all production classes across five logical layers:
**Model**, **Repository**, **Service**, **Server/Client Network**, and **TQL DSL**.
Enumerations and the protocol DTOs are included. UI controllers are excluded from
the class diagram (they are thin view binders with no business logic).

---

## 2. Enumerations

```mermaid
classDiagram
    class Role {
        <<enumeration>>
        STUDENT
        STAFF
        ADMIN
    }

    class Priority {
        <<enumeration>>
        LOW
        MEDIUM
        HIGH
        CRITICAL
        +compareTo(other) int
        +bump() Priority
    }

    class TicketStatus {
        <<enumeration>>
        OPEN
        IN_PROGRESS
        ESCALATED
        RESOLVED
        CLOSED
        +canTransitionTo(next) boolean
    }
```

---

## 3. Model Layer

```mermaid
classDiagram
    class User {
        -int id
        -String username
        -String passwordHash
        -Role role
        -String fullName
        -String email
        -boolean isActive
        -LocalDateTime createdAt
        +getId() int
        +getUsername() String
        +getRole() Role
        +isActive() boolean
    }

    class Student {
    }

    class StaffMember {
        -int departmentId
        +getDepartmentId() int
    }

    class Admin {
    }

    class Ticket {
        -int id
        -String title
        -String description
        -Priority priority
        -TicketStatus status
        -int categoryId
        -int submitterId
        -Integer assignedStaffId
        -LocalDateTime createdAt
        -LocalDateTime updatedAt
        -LocalDateTime resolvedAt
        +getId() int
        +getPriority() Priority
        +getStatus() TicketStatus
        +compareTo(other Ticket) int
    }

    class TicketNote {
        -int id
        -int ticketId
        -int authorId
        -String note
        -LocalDateTime createdAt
        +getTicketId() int
        +getNote() String
    }

    class Category {
        -int id
        -String name
        -String description
        -int departmentId
        -boolean isActive
        +getId() int
        +getName() String
        +getDepartmentId() int
    }

    class Department {
        -int id
        -String name
        -String description
        +getId() int
        +getName() String
    }

    class AuditLog {
        -int id
        -int actorId
        -String action
        -String entityType
        -int entityId
        -LocalDateTime timestamp
        -String details
    }

    class TicketFilter {
        -String status
        -String priority
        -String category
        -String department
        -String assignedUsername
        -String submitterUsername
        +hasStatus() boolean
        +hasPriority() boolean
        +toSqlWhere(role, userId) String
    }

    class MessageRequest {
        -String type
        -String sessionId
        -String payloadJson
        +getType() String
        +getSessionId() String
        +toJson() String
        +fromJson(raw) MessageRequest$
    }

    class MessageResponse {
        -String status
        -String errorCode
        -String dataJson
        +isOk() boolean
        +getErrorCode() String
        +toJson() String
        +fromJson(raw) MessageResponse$
    }

    User <|-- Student
    User <|-- StaffMember
    User <|-- Admin
    Ticket "many" --> "1" Category : categorised by
    Ticket "many" --> "1" Department : routed to
    TicketNote "many" --> "1" Ticket : notes on
    Category "many" --> "1" Department : belongs to
```

---

## 4. Repository Layer

```mermaid
classDiagram
    class UserRepository {
        -Connection conn
        +findById(id int) Optional~User~
        +findByUsername(username String) Optional~User~
        +findAll() List~User~
        +save(user User) User
        +update(user User) void
        +setActive(userId int, active boolean) void
    }

    class TicketRepository {
        -Connection conn
        +findById(id int) Optional~Ticket~
        +findBySubmitter(userId int) List~Ticket~
        +findByAssignedStaff(staffId int) List~Ticket~
        +findAll() List~Ticket~
        +save(ticket Ticket) Ticket
        +update(ticket Ticket) void
        +saveNote(note TicketNote) void
        +findNotes(ticketId int) List~TicketNote~
        +search(filter TicketFilter, role Role, userId int) List~Ticket~
    }

    class CategoryRepository {
        -Connection conn
        +findById(id int) Optional~Category~
        +findAllActive() List~Category~
        +findAll() List~Category~
        +save(category Category) Category
        +setActive(categoryId int, active boolean) void
        +findDepartmentById(id int) Optional~Department~
        +findAllDepartments() List~Department~
    }

    class AuditLogRepository {
        -Connection conn
        +log(actorId int, action String, entityType String, entityId int, details String) void
        +findAll() List~AuditLog~
        +findByEntity(type String, id int) List~AuditLog~
    }

    class DatabaseManager {
        -static String DB_URL
        -static Connection sharedConnection
        +static getConnection() Connection
        +static initialiseSchema() void
        +static seedData() void
    }

    UserRepository --> DatabaseManager
    TicketRepository --> DatabaseManager
    CategoryRepository --> DatabaseManager
    AuditLogRepository --> DatabaseManager
```

---

## 5. Service Layer

```mermaid
classDiagram
    class AuthService {
        -UserRepository userRepo
        -ConcurrentHashMap~String,Session~ sessions
        -ScheduledExecutorService sessionCleaner
        -int sessionTimeoutMinutes
        +authenticate(username String, password String) Optional~String~
        +validateSession(sessionId String) Optional~User~
        +cleanExpiredSessions() void
        +logout(sessionId String) void
        +invalidateUserSessions(userId int) void
    }

    class Session {
        -String sessionId
        -int userId
        -Role role
        -LocalDateTime createdAt
        -LocalDateTime lastAccessedAt
        +getUserId() int
        +getRole() Role
        +touch() void
        +isExpired(timeoutMinutes int) boolean
    }

    class TicketService {
        -TicketRepository ticketRepo
        -TicketQueueManager queueManager
        -RoutingService routingService
        -AuditLogRepository auditRepo
        +submitTicket(dto TicketSubmitDto, submitterId int) Ticket
        +claimNextTicket(staffId int, departmentId int) Optional~Ticket~
        +updateStatus(ticketId int, newStatus TicketStatus, note String, actorId int) void
        +escalateTicket(ticketId int, reason String, staffId int) void
        +getTicket(ticketId int) Optional~Ticket~
        +getMyTickets(userId int) List~Ticket~
        +getAssignedTickets(staffId int) List~Ticket~
        +getAllTickets() List~Ticket~
        +searchTickets(filter TicketFilter, role Role, userId int) List~Ticket~
        -validateTransition(current TicketStatus, next TicketStatus) void
    }

    class TicketSubmitDto {
        +String title
        +String description
        +int categoryId
        +Priority priority
    }

    class RoutingService {
        -CategoryRepository categoryRepo
        +route(ticket Ticket) String
        +getDepartmentForCategory(categoryId int) String
    }

    class TicketQueueManager {
        -Map~String,PriorityBlockingQueue~Ticket~~ departmentQueues
        -ReentrantLock enqueueLock
        +initialise(departments List~Department~) void
        +enqueue(ticket Ticket, department String) void
        +dequeue(department String) Optional~Ticket~
        +remove(ticketId int, department String) boolean
        +size(department String) int
        +peek(department String) Optional~Ticket~
    }

    class AnalyticsService {
        -TicketRepository ticketRepo
        -ReentrantReadWriteLock cacheLock
        -AnalyticsReport cachedReport
        -LocalDateTime cacheExpiry
        +getReport() AnalyticsReport
        +invalidateCache() void
        -loadReport() AnalyticsReport
    }

    class AnalyticsReport {
        +Map~String,Integer~ ticketsByStatus
        +Map~String,Integer~ ticketsByCategory
        +Map~String,Double~ avgResolutionTimeByDept
        +Map~String,Integer~ openTicketsByStaff
        +LocalDateTime generatedAt
    }

    class UserManagementService {
        -UserRepository userRepo
        -AuthService authService
        -AuditLogRepository auditRepo
        +createUser(dto UserCreateDto, adminId int) User
        +deactivateUser(userId int, adminId int) void
        +getAllUsers() List~User~
    }

    class CategoryService {
        -CategoryRepository categoryRepo
        -AuditLogRepository auditRepo
        +createCategory(name String, desc String, deptId int, adminId int) Category
        +deactivateCategory(categoryId int, adminId int) void
        +getAllActiveCategories() List~Category~
        +getAllDepartments() List~Department~
    }

    AuthService --> UserRepository
    AuthService *-- Session
    TicketService --> TicketRepository
    TicketService --> TicketQueueManager
    TicketService --> RoutingService
    TicketService --> AuditLogRepository
    RoutingService --> CategoryRepository
    AnalyticsService --> TicketRepository
    UserManagementService --> UserRepository
    UserManagementService --> AuthService
    UserManagementService --> AuditLogRepository
    CategoryService --> CategoryRepository
    CategoryService --> AuditLogRepository
```

---

## 6. Server & Client Network Layer

```mermaid
classDiagram
    class ServerConfig {
        -int port
        -int threadPoolSize
        -int sessionTimeoutMinutes
        -String dbPath
        +static load() ServerConfig
        +getPort() int
        +getThreadPoolSize() int
        +getSessionTimeoutMinutes() int
        +getDbPath() String
    }

    class PasswordUtil {
        +static hash(plaintext String) String
        +static verify(plaintext String, hash String) boolean
    }

    class HelpdeskServer {
        -ServerSocket serverSocket
        -ExecutorService threadPool
        -ServerConfig config
        -boolean running
        -AuthService authService
        -TicketService ticketService
        -UserManagementService userService
        -CategoryService categoryService
        -AnalyticsService analyticsService
        +start() void
        +stop() void
        -acceptConnections() void
    }

    class ClientHandler {
        -Socket clientSocket
        -BufferedReader reader
        -PrintWriter writer
        -HelpdeskServer server
        +run() void
        -dispatch(request MessageRequest) MessageResponse
        -handleLogin(payload String) MessageResponse
        -handleSubmitTicket(payload String, user User) MessageResponse
        -handleClaimTicket(payload String, user User) MessageResponse
        -handleUpdateStatus(payload String, user User) MessageResponse
        -handleEscalate(payload String, user User) MessageResponse
        -handleSearch(payload String, user User) MessageResponse
        -handleGetAnalytics(user User) MessageResponse
        -requireRole(user User, roles Role[]) void
    }

    class SocketClient {
        -String host
        -int port
        -Socket socket
        -BufferedReader reader
        -PrintWriter writer
        -String sessionId
        +connect() void
        +disconnect() void
        +send(request MessageRequest) MessageResponse
        +isConnected() boolean
    }

    HelpdeskServer --> ServerConfig : loads
    HelpdeskServer --> ClientHandler : creates per connection
    HelpdeskServer --> AuthService
    HelpdeskServer --> TicketService
    HelpdeskServer --> UserManagementService
    HelpdeskServer --> CategoryService
    HelpdeskServer --> AnalyticsService
    AuthService ..> PasswordUtil : uses
    ClientHandler --> HelpdeskServer : references services via
    SocketClient ..> HelpdeskServer : TCP connects to
```

---

## 7. TQL — Ticket Query Language Layer

```mermaid
classDiagram
    class TicketQueryLexer {
        <<ANTLR Generated>>
        +nextToken() Token
    }

    class TicketQueryParser {
        <<ANTLR Generated>>
        +query() QueryContext
    }

    class TicketQueryVisitorImpl {
        +visitQuery(ctx QueryContext) TicketFilter
        +visitFilterExpr(ctx FilterExprContext) void
        +visitValue(ctx ValueContext) String
    }

    class TicketFilter {
        -String status
        -String priority
        -String category
        -String department
        -String assignedUsername
        -String submitterUsername
        +hasStatus() boolean
        +hasPriority() boolean
        +toSqlWhere(role Role, userId int) String
    }

    TicketQueryParser --> TicketQueryLexer : uses
    TicketQueryVisitorImpl --> TicketQueryParser : visits parse tree of
    TicketQueryVisitorImpl --> TicketFilter : produces
```

---

## 8. Key Design Patterns Applied

| Pattern | Where Applied | Purpose |
|---|---|---|
| **Inheritance** | `Student`, `StaffMember`, `Admin` extend `User` | Role-specific behaviour; polymorphic dispatch |
| **Strategy** | `RoutingService.route()` — strategy could be swapped for different routing algorithms | Routing logic decoupled from `TicketService` |
| **Repository** | All `*Repository` classes | Isolate data access; enable in-memory SQLite for testing |
| **DTO** | `TicketSubmitDto`, `UserCreateDto`, `AnalyticsReport` | Decouple transport from domain objects |
| **Singleton** | `DatabaseManager.getConnection()` | Single shared connection per thread |
| **Observer (implicit)** | `AnalyticsService.invalidateCache()` called after every ticket update | Cache coherence |
| **Template Method** | `ClientHandler.dispatch()` — open switch on message type | Uniform request dispatching |
| **Comparable** | `Ticket implements Comparable<Ticket>` | Natural ordering for `PriorityBlockingQueue` |

---

## Document History

| Version | Date | Author | Notes |
|---|---|---|---|
| 1.0 | 2026-11-19 | Project Team | Initial class diagram |
