# CampusFlow

> **Smart Campus Helpdesk & Queue Management System**
>
> A Java desktop application that centralises student complaint submission, routes
> service requests to the right department, and gives administrators real-time
> visibility over every open ticket — all backed by thread-safe queues and Java
> socket-based client-server communication.

---

## Overview

**CampusFlow** is developed as part of a Software Construction and Development course
project. University campuses handle hundreds of daily service requests — broken
equipment, software issues, academic queries — through disconnected channels such as
email, WhatsApp groups, and walk-in desks. This creates lost tickets, uneven workloads,
and zero accountability.

CampusFlow replaces that chaos with a structured helpdesk: students submit complaints
through a client application, a central server queues and routes each ticket by
department and priority, and staff resolve requests within a managed workflow. Admins
gain full oversight and analytics.

---

## Core Features

| # | Feature | Description |
|---|---|---|
| 1 | **Complaint Submission & Tracking** | Students submit complaints and follow their ticket status in real time |
| 2 | **Service Request Routing** | Requests are automatically routed to IT, Maintenance, or Academic departments |
| 3 | **Staff Assignment & Resolution** | Staff receive assigned tickets, update status, and close complaints |
| 4 | **Admin User & Category Management** | Administrators manage accounts, departments, and complaint categories |
| 5 | **Priority-Based Ticket Management** | Tickets are ranked (Low / Medium / High / Critical) and queued accordingly |
| 6 | **Thread-Safe Request Queues** | Concurrent submissions handled safely via Java concurrency primitives |
| 7 | **Socket-Based Client-Server Communication** | Java `java.net` sockets connect client UI to the central helpdesk server |
| 8 | **Complaint Analytics & Reporting** | Dashboards and exportable reports on resolution times, volume, and trends |

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| UI Framework | JavaFX 21 |
| Client-Server | Java `java.net` Sockets (separate server process + desktop clients) |
| Concurrency | `java.util.concurrent` (Thread Pool, `PriorityBlockingQueue`, `ScheduledExecutorService`) |
| Password Hashing | BCrypt (via `jBCrypt`) |
| DSL / Query Language | ANTLR4 (Ticket Query Language — TQL) |
| Database | SQLite (via `sqlite-jdbc`) |
| Logging | SLF4J 2 + Logback |
| Testing | JUnit 5 (Jupiter) |
| Build Tool | Apache Maven 3.10+ |

> Java sockets and concurrency primitives are part of the Java standard library —
> no additional networking or threading dependencies are required.

---

## Architecture

CampusFlow follows a **clean layered architecture** with a client-server split:

```
  CLIENT (JavaFX Desktop App)            SERVER (Helpdesk Engine)
  ┌──────────────────────────┐           ┌──────────────────────────┐
  │   Presentation Layer     │           │   Request Handler Layer  │
  │  JavaFX Controllers/FXML │  socket   │  Socket listener threads │
  ├──────────────────────────┤ ◄──────► ├──────────────────────────┤
  │   Service Layer          │           │   Service Layer          │
  │  Complaint/auth logic    │           │  Queue, routing, priority│
  ├──────────────────────────┤           ├──────────────────────────┤
  │   Model Layer            │           │   Repository Layer       │
  │  Ticket, User, Category  │           │  JDBC → SQLite           │
  └──────────────────────────┘           └──────────────────────────┘
```

---

## Project Structure

```
CampusFlow/
├── docs/
│   ├── requirements/       # Project overview, user stories, SRS
│   ├── architecture/       # Architectural decision records (ADRs)
│   ├── uml/                # Class, sequence, and use-case diagrams
│   └── testing/            # Test plans and reports
├── src/
│   ├── main/
│   │   ├── java/           # Application source code
│   │   └── resources/      # FXML views, CSS, config files
│   └── test/
│       └── java/           # JUnit 5 test classes
├── .gitignore
├── README.md
└── pom.xml
```

---

## Getting Started

### Prerequisites

- Java 21 (JDK)
- Apache Maven 3.10+
- Git

### Clone & Build

```bash
git clone https://github.com/MuhammadJoyo/CampusFlow.git
cd CampusFlow
mvn validate        # Verify project structure and POM
mvn compile         # Compile sources
mvn test            # Run JUnit 5 test suite
mvn javafx:run      # Launch the client application (once implemented)
```

---

## Documentation

| Document | Location |
|---|---|
| Project Overview | [`docs/requirements/PROJECT_OVERVIEW.md`](docs/requirements/PROJECT_OVERVIEW.md) |
| Architecture Decisions | `docs/architecture/` |
| UML Diagrams | `docs/uml/` |
| Test Plans | `docs/testing/` |

---

## Team

Developed by students of the **Software Construction and Development** course.

- **Repository**: [github.com/MuhammadJoyo/CampusFlow](https://github.com/MuhammadJoyo/CampusFlow)

---

## Status

> 🚧 **Foundation phase** — Project scaffolding complete. Application modules pending approval.

---

## License

This project is developed for academic purposes under the university course guidelines.
