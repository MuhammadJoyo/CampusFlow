# CampusFlow — Project Overview

> **Document type**: Requirements — Project Overview
> **Version**: 1.1
> **Date**: 2026-11-19
> **Status**: Draft

---

## 1. Problem Statement

University campuses generate a continuous stream of service requests every day —
broken lab equipment, network outages, locked rooms, lost student IDs, software
licence issues, and academic administrative queries. In most institutions these
requests are handled through ad-hoc, disconnected channels:

- **Email** — requests get buried, forwarded incorrectly, or simply forgotten
- **WhatsApp / phone calls** — no paper trail, no accountability, no escalation path
- **Walk-in desks** — bottlenecks during peak periods; no way to track progress remotely
- **Spreadsheets** — manually updated, out of date the moment a ticket status changes

This fragmentation causes measurable harm:

| Pain Point | Impact |
|---|---|
| Lost or duplicate tickets | Students re-submit the same complaint multiple times |
| No priority distinction | A critical server outage waits behind a cosmetic paint request |
| Invisible workload | Managers cannot see which staff are overloaded or idle |
| Zero accountability | No record of who handled a complaint, when, or how long it took |
| No analytics | Recurring issues go undetected because no data is ever aggregated |

---

## 2. Proposed Solution

**CampusFlow** is a **Smart Campus Helpdesk & Queue Management System** — a Java
desktop application with a client-server architecture that provides:

- A **student-facing client** for submitting and tracking complaints
- A **central helpdesk server** that queues, prioritises, and routes tickets to the
  correct department using thread-safe Java concurrency primitives
- A **staff interface** for receiving assigned tickets and updating resolution status
- An **admin console** for managing users, departments, complaint categories, and
  viewing analytics

### Key Design Principles

| Principle | Description |
|---|---|
| **Single source of truth** | All ticket and user data persists in one SQLite database on the server |
| **Priority-aware queuing** | Tickets are ranked (Low / Medium / High / Critical) and served accordingly |
| **Thread-safe concurrency** | Multiple simultaneous submissions are handled via `java.util.concurrent` queues |
| **Socket communication** | Client and server communicate over Java `java.net` TCP sockets |
| **Role-based access** | Students, Staff, and Admins each see only their relevant views |
| **Auditability** | Every status change is timestamped and attributed to a named user |

### High-Level Feature Areas *(detailed in subsequent SRS documents)*

1. **Complaint Submission & Tracking** — students create tickets and monitor status
2. **Service Request Routing** — automatic dispatch to IT, Maintenance, or Academic departments
3. **Staff Assignment & Resolution** — staff manage their assigned ticket queue
4. **Admin User & Category Management** — full control over accounts and taxonomy
5. **Priority-Based Ticket Management** — urgency levels with queue-ordering enforcement
6. **Thread-Safe Request Queues** — concurrent-safe data structures on the server
7. **Socket-Based Client-Server Communication** — TCP socket protocol between client and server
8. **Complaint Analytics & Reporting** — resolution-time trends, volume by category, SLA tracking

---

## 3. Users (Stakeholders)

### 3.1 Primary Users

| Role | Description | Core Needs |
|---|---|---|
| **Student** | Any enrolled student experiencing a campus-related issue | Submit a complaint, attach details, choose category, track ticket status, receive updates |
| **Staff Member** | IT technician, maintenance worker, or academic administrator | View assigned tickets, update status (In Progress / Resolved), add resolution notes |
| **Administrator** | System owner responsible for the helpdesk platform | Manage user accounts, define complaint categories and departments, view all tickets, generate reports |

### 3.2 Secondary Stakeholders

| Stakeholder | Interest |
|---|---|
| **Department Head** | Needs visibility into their team's workload and performance metrics |
| **University Management** | Requires high-level analytics on campus service quality |
| **IT Support (internal)** | Responsible for server installation, database backups, and runtime troubleshooting |

---

## 4. Project Objectives

### 4.1 Functional Objectives

1. Allow students to submit complaints with a title, description, category, and priority level.
2. Persist all tickets in an SQLite database on the server and assign each a unique ticket ID.
3. Route each ticket to the appropriate department queue based on its category.
4. Notify assigned staff of new tickets via the client-server socket channel.
5. Allow staff to accept, update, escalate, or close tickets with timestamped notes.
6. Allow administrators to create/deactivate user accounts and manage complaint categories.
7. Enforce priority-ordered serving within each department queue.
8. Present administrators with an analytics dashboard: ticket volume, average resolution time, open-by-category breakdown.

### 4.2 Quality Objectives

| Quality Attribute | Target |
|---|---|
| **Thread Safety** | Server handles ≥ 10 concurrent client connections without race conditions or data corruption |
| **Reliability** | No ticket data is lost due to unhandled exceptions; server recovers gracefully from client disconnections |
| **Maintainability** | Layered architecture and consistent coding standards enable addition of new departments or categories without core changes |
| **Testability** | Business logic (queue management, routing rules, priority ordering) is isolated from UI and network layers for unit testing |
| **Usability** | A student can submit a complaint within 3 interactions from the main screen |
| **Performance** | Ticket submission acknowledged by the server within 1 second under normal load |

### 4.3 Project (Course) Objectives

1. Apply software engineering principles: requirements analysis, system design, implementation, and testing.
2. Demonstrate Java concurrency concepts (`BlockingQueue`, thread pools, `synchronized`) in a real-world context.
3. Implement TCP socket programming for client-server message exchange.
4. Practice version-controlled, collaborative development using Git and GitHub.
5. Produce professional artefacts: SRS, UML diagrams (use-case, class, sequence), and test plans.
6. Deliver a working, documented software product by the end of the academic term.

---

## 5. Constraints & Assumptions

### Constraints

| Constraint | Detail |
|---|---|
| **Platform** | Windows desktop application (JavaFX client + Java server process) |
| **Runtime** | Java 21; no external application server or container |
| **Database** | SQLite only — embedded on the server side, no separate DB server |
| **Network** | Local area network (LAN); sockets connect over `localhost` or LAN IP |
| **Internet** | Not required at runtime; used only for GitHub collaboration |
| **Timeline** | Academic semester schedule |

### Assumptions

- Each user has exactly one role (Student, Staff, or Administrator)
- The server process runs on a single machine accessible to all clients on the same LAN
- Passwords will be stored hashed (no production deployment assumed, but secure practices applied)
- All UI text is in English
- Attachments (files, images) are out of scope for the initial version

---

## 6. Out of Scope

The following are explicitly outside the scope of this project to prevent scope creep:

- Course scheduling, timetable management, or room booking
- Grade entry, GPA calculation, or academic transcript generation
- Event management or campus calendar features
- Mobile or web-based client interfaces
- Integration with third-party university ERP systems (Banner, SAP, etc.)
- Email or SMS notification dispatch via external services
- Payment processing or financial modules
- File/image attachment support in tickets (initial version)

---

## 7. Document History

| Version | Date | Author | Change |
|---|---|---|---|
| 1.0 | 2026-11-19 | Project Team | Initial draft |
| 1.1 | 2026-11-19 | Project Team | Corrected scope to Smart Campus Helpdesk & Queue Management System; removed unrelated scheduling, room booking, and grade management content |
