# Company App – Architecture & Design Specification

## 1. Purpose of This Document

This document summarizes **all architectural decisions and constraints agreed so far**. It acts as a **living reference** you can point to in future discussions so guidance stays consistent as the system evolves.

Target audience:
- You (junior engineer, primary builder)
- Me (mentor / reviewer)

This document is intentionally **descriptive, not prescriptive**. It explains *what* was chosen and *why*, without implementing anything for you.

---

## 2. Problem Statement (Business Context)

The application is an **internal company app** used by employees to:

- Log in
- Enter business values/data
- View a shared dashboard of aggregated data

Platforms:
- Android
- iOS

Constraints:
- Small team
- Low cost (free tools, free hosting tiers)
- Maintainability and clarity prioritized over speed

---

## 3. High-Level Architecture

```
Mobile Clients (Android + iOS)
        ↓
Kotlin Multiplatform (composeApp/commonMain + shared module)
        ↓
Kotlin Backend (Javalin)
        ↓
PostgreSQL Database
```

Key principle:
> **Single language (Kotlin) across mobile, shared logic, and backend** to reduce complexity.

---

## 4. Technology Stack (Final Decisions)

### Mobile
- **Kotlin Multiplatform** with **Compose Multiplatform** wizard structure
- `composeApp` module: Android app + UI-related shared code (ViewModels, Compose UI)
- `shared` module: Pure business logic shared with backend (DTOs, domain models, validation)
- `iosApp` module: Xcode project consuming the shared framework

### Backend
- Kotlin (JVM)
- Javalin (HTTP server)
- HikariCP (DB connection pool)
- kotlin-logging

### Database
- PostgreSQL
- Flyway for schema migrations

### Infrastructure
- Docker
- Docker Compose (local dev)

### Tooling
- IntelliJ Community Edition
- Postman / Bruno for API testing
- DBeaver Community for database inspection

### Quality
- Detekt (static analysis)
- Ktlint (formatting)
- SonarLint (IDE-level feedback)

---

## 5. Explicit Non-Goals / Exclusions

- No Node.js / Express backend
- No Spring Boot
- No paid IDEs or hosting
- No frontend web dashboard (mobile only, for now)

---

## 6. Repository Structure

```
company-app/
├── .gradle/
├── .idea/
├── .kotlin/
├── build/
│
├── composeApp/                    # KMP mobile (keep wizard-generated structure)
│   ├── src/
│   │   ├── androidMain/           # Android entry point, Android-specific UI
│   │   ├── commonMain/            # UI-related shared code (ViewModels, Compose UI)
│   │   ├── commonTest/
│   │   └── iosMain/               # iOS-specific expect/actual
│   └── build.gradle.kts
│
├── iosApp/                        # iOS entry point (keep wizard-generated)
│   └── iosApp.xcodeproj
│
├── shared/                        # Shared between mobile + backend
│   ├── src/
│   │   ├── commonMain/            # Pure business logic (DTOs, domain, validation)
│   │   │   └── kotlin/com/company/
│   │   │       ├── dto/
│   │   │       ├── domain/
│   │   │       └── validation/
│   │   └── commonTest/
│   └── build.gradle.kts
│
├── backend/                       # Javalin backend (separate module)
│   ├── src/
│   │   └── main/kotlin/com/company/backend/
│   │       ├── api/routes/        # HTTP endpoints
│   │       ├── service/           # Business logic
│   │       ├── repository/        # Database access
│   │       ├── database/          # Connection config, Flyway
│   │       ├── auth/              # JWT handling
│   │       ├── config/            # App configuration
│   │       └── Main.kt
│   └── build.gradle.kts
│
├── migrations/                    # Flyway SQL files
│   ├── V1__create_users.sql
│   └── V2__create_entries.sql
│
├── docker/                        # Docker configs
│   ├── docker-compose.yml
│   └── postgres/
│
├── docs/                          # Documentation
│   └── architecture.md (this spec)
│
├── gradle/
├── settings.gradle.kts            # Must include :shared and :backend
└── build.gradle.kts
```

### Module Dependencies

```
composeApp ──depends on──> shared
backend ─────depends on──> shared
iosApp ──────imports─────> shared (as framework)
```

### Key Principles

1. **`composeApp` keeps wizard structure** – Don't rename or restructure what was generated
2. **`shared` is for pure logic** – No UI code, no HTTP server code, no SQL
3. **`backend` depends only on `shared`** – Never depends on `composeApp` or `iosApp`
4. **`iosApp` imports `shared`** – Through the KMP framework embedding mechanism

### Gradle Configuration

Update `settings.gradle.kts`:

```kotlin
include(":composeApp")
include(":shared")
include(":backend")
```

---

## 7. Backend Layering Rules

### Backend Location
The `backend/` directory is a **separate module** at project root. It depends only on `shared`, never on mobile modules.

### 7.1 Routes (`backend/api/routes`)

Responsibilities:
- Define HTTP endpoints
- Parse requests
- Perform authentication checks
- Call services
- Return responses

Constraints:
- No business logic
- No SQL

---

### 7.2 Services (`backend/service`)

Responsibilities:
- Business rules
- Validation
- Permission checks
- Transaction boundaries
- Trigger audit logging

Constraints:
- No HTTP concerns
- No raw SQL

---

### 7.3 Repositories (`backend/repository`)

Responsibilities:
- Database queries
- Mapping rows to domain models

Constraints:
- No business logic
- No HTTP logic

---

### 7.4 Database (`backend/database`)

Responsibilities:
- HikariCP configuration
- Flyway initialization
- Connection management

---

## 8. Database Migrations

### Purpose

Database migrations track **schema changes over time** in a safe, repeatable way.

They prevent:
- Environment drift
- Manual DB edits
- Production inconsistencies

---

### Structure

```
migrations/
 ├── V1__create_users.sql
 ├── V2__create_entries.sql
 └── V3__create_audit_log.sql
```

---

## 9. Audit Logging (Business History)

### Definition

An **audit log** is a database table that records **important business events**, not technical logs.

Examples:
- User login
- Entry creation
- Entry update
- Entry deletion

---

### Audit Log Is NOT

- Application logs
- Debug output
- Stack traces

---

### Minimal Audit Log Table (Initial)

Fields:
- user_id
- action
- timestamp

Optional later expansion:
- entity_type
- entity_id
- old_value / new_value (JSON)

---

### Where Audit Logging Happens

- **Service layer only**
- Never in routes
- Never in repositories

---

## 10. Authentication & Authorization

### Authentication

- JWT-based authentication
- Token issued on login
- Token attached to API requests

---

### Authorization

- Role-based access control

Example roles:
- ADMIN
- MANAGER
- EMPLOYEE
- VIEWER

---

## 11. Kotlin Multiplatform Sharing Strategy

### Module: `shared` (Root-level)

**Shared between:** Mobile (Android/iOS) + Backend

Contents:
- DTOs (API request/response classes)
- Domain models (business entities)
- Validation rules
- Serialization logic

### Module: `composeApp` (Wizard-generated)

**Shared between:** Android + iOS only

Contents:
- ViewModels
- Compose UI components
- Platform-specific UI abstractions
- Client-side repositories (caching, offline logic)

### NOT Shared Anywhere

- HTTP server code (stays in `backend`)
- SQL / repositories (stays in `backend`)
- UI implementation details (stays in `composeApp/androidMain` and `iosApp`)

---

## 12. Logging vs Auditing (Important Distinction)

| Logging | Auditing |
|-------|--------|
| Technical | Business |
| Text-based | Database |
| Debugging | Accountability |
| Temporary | Permanent |

---

## 13. Development Phases (Learning-Oriented)

### Phase 1 – Backend Core
- Javalin setup
- Postgres via Docker
- Flyway migrations
- Authentication

### Phase 2 – Business Features
- Entry submission
- Dashboard aggregation APIs

### Phase 3 – Mobile Apps
- **Android**: Run `composeApp` configuration in Android Studio
- **iOS**: Open `iosApp/iosApp.xcodeproj` in Xcode or use Android Studio's iOS run configuration
- Implement login screen in `composeApp/src/androidMain` and SwiftUI in `iosApp/`
- Share ViewModels in `composeApp/src/commonMain`

### Phase 4 – Enhancements
- Realtime updates (SSE or WebSockets)
- Audit visibility
- CI/CD

---

## 14. Guiding Principles

- Prefer clarity over cleverness
- Keep layers thin and explicit
- One responsibility per module
- Build the smallest thing that works
- Learn by doing, not by optimizing early

---

## 15. How This Document Is Used

- You code against it
- When confused, refer back here
- When asking questions, reference section numbers
- When requirements change, update this document

This is the **source of truth** for architectural intent.
