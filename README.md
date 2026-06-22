# Ticket Booking System

A production-thinking backend built with Spring Boot, demonstrating real concurrency handling, pessimistic locking, Redis soft locks, JWT authentication, Flyway migrations, and transactional booking flows.

> **Status: Core Booking System Complete (~85%)** — Remaining work includes Circuit Breaker, Idempotency Keys, Optimistic Locking Comparison, and Deployment.

---

## What This Project Demonstrates

- Pessimistic locking to guarantee seat exclusivity under concurrent requests
- Redis soft lock as a UX optimization layer (with graceful DB fallback)
- JWT access token + refresh token authentication
- Single active refresh token per user
- Modular monolith architecture with clean separation of concerns
- Concurrency-tested booking flow
- Transactional booking and payment processing
- Flyway-managed database schema migrations
- Swagger/OpenAPI documentation with JWT support
- Docker-based local development environment

---

## Architecture

```text
┌─────────────────────────────────────────────────────────┐
│                  Spring Boot Application                │
│                                                         │
│  ┌──────┐  ┌───────┐  ┌───────┐  ┌─────────┐            │
│  │ auth │  │ event │  │ seat  │  │ booking │            │
│  └──────┘  └───────┘  └───────┘  └─────────┘            │
│       ↓          ↓          ↓           ↓               │
│  ┌──────────────────────────────────────────────┐       │
│  │             PostgreSQL Database              │       │
│  └──────────────────────────────────────────────┘       │
│                                                         │
│  ┌──────────────────────────────────────────────┐       │
│  │     Redis (Seat Hold / Soft Lock Layer)      │       │
│  └──────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────┘
```

### Why Modular Monolith?

Microservices introduce distributed transaction challenges (sagas, eventual consistency, message coordination) that are unnecessary for a ticket booking system where booking, payment, and seat updates should remain atomic.

A modular monolith provides:

- Strong transactional consistency
- Simpler deployment
- Easier debugging
- Clear module boundaries
- Room for future service extraction if needed

---

## Authentication Flow

```text
Login Request
      │
      ▼
Generate JWT Access Token
      │
      ▼
Client Stores Token
      │
      ▼
Authorization Header
Bearer <token>
      │
      ▼
JWT Filter Validation
      │
      ▼
Authenticated Request
```

Refresh Tokens:

```text
Login
  │
  ▼
Access Token + Refresh Token
  │
  ▼
Access Token Expires
  │
  ▼
/auth/refresh
  │
  ▼
New Access Token
```

---

## Booking Flow

```text
User → POST /bookings
         │
         ▼
BookingService (@Transactional)
         │
         ├─→ Acquire PESSIMISTIC_WRITE lock
         │
         ├─→ Validate Seat Availability
         │
         ├─→ Verify Redis Hold Ownership
         │
         ├─→ Simulate Payment
         │
         ├─→ Update Seat Status
         │
         ├─→ Save Booking
         │
         └─→ Release Redis Hold
         │
         ▼
      Response
```

---

## Locking Strategy

### Database Locking (Source of Truth)

The booking system uses:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

to guarantee that only one transaction can modify a seat at a time.

Benefits:

- Prevents double booking
- Prevents race conditions
- Guarantees seat consistency

---

### Redis Soft Lock (Optimization Layer)

When a user selects a seat:

```text
POST /seats/hold
        │
        ▼
SET seat_lock:{eventId}:{seatId}
        userId
        NX EX 300
```

Example:

```text
seat_lock:1:5 = 101
```

Meaning:

```text
Event 1
Seat 5
Held by User 101
```

Redis provides:

- Better user experience
- Temporary reservation
- Reduced database contention
- Automatic expiration (TTL)

If Redis becomes unavailable:

```text
Redis Failure
      │
      ▼
Continue Request
      │
      ▼
Database Lock Still Protects System
```

Database locking remains the source of truth.

---

## Concurrency Testing

The booking flow was verified using concurrent test execution.

### Scenario

```text
8 Users
1 Seat
8 Concurrent Booking Attempts
```

### Tools Used

- ExecutorService
- CountDownLatch
- Future
- JUnit 5

### Result

```text
✓ Exactly 1 booking succeeds
✓ Remaining requests fail safely
✓ No double booking occurs
✓ Data consistency maintained
```

This validates that pessimistic locking correctly protects inventory under high contention.

---

## API Endpoints

### Authentication

| Method | Endpoint | Description |
|----------|----------|-------------|
| POST | `/auth/register` | Register user |
| POST | `/auth/login` | Login user |
| POST | `/auth/refresh` | Generate new access token |
| POST | `/auth/logout` | Logout user |

### Events

| Method | Endpoint | Description |
|----------|----------|-------------|
| POST | `/events` | Create event |
| GET | `/events` | Get all events |
| GET | `/events/{id}` | Get event by ID |

### Seats

| Method | Endpoint | Description |
|----------|----------|-------------|
| POST | `/seats` | Create seat |
| GET | `/seats/{id}` | Get seat by ID |
| GET | `/seats/event/{eventId}` | Get seats by event |
| GET | `/seats/event/{eventId}/available` | Get available seats |
| POST | `/seats/hold` | Hold seat in Redis |

### Bookings

| Method | Endpoint | Description |
|----------|----------|-------------|
| POST | `/bookings` | Create booking |
| GET | `/bookings/{id}` | Get booking by ID |

---

## API Documentation

Swagger/OpenAPI is integrated for API exploration and testing.

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

Features:

- JWT Authorization support
- Interactive endpoint testing
- Request/Response schemas
- OpenAPI 3 documentation

---

## Tech Stack

| Layer | Technology |
|---------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Security | Spring Security |
| Authentication | JWT |
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Cache | Redis |
| Database Migrations | Flyway |
| API Documentation | Swagger / OpenAPI |
| Testing | JUnit 5 |
| Build Tool | Maven |
| Containerization | Docker |

---

## Database Versioning

Schema changes are managed using Flyway migrations.

Current migrations:

```text
V1__init.sql
V2__seed_roles.sql
V3__update_seat_schema.sql
V4__update_bookings_schema.sql
```

Benefits:

- Reproducible database setup
- Version-controlled schema evolution
- Consistent environments across machines

---

## Database Schema

```text
roles
 └── id
 └── name

users
 └── id
 └── email
 └── password
 └── role_id

refresh_tokens
 └── id
 └── user_id
 └── token
 └── expiry_date

events
 └── id
 └── name
 └── location
 └── event_time

seats
 └── id
 └── seat_number
 └── status
 └── event_id
 └── created_at
 └── updated_at

bookings
 └── id
 └── user_id
 └── seat_id
 └── booked_at
```

---

## Failure Scenarios

| Scenario | Handling Strategy |
|-----------|------------------|
| Two users book same seat | Pessimistic DB lock |
| Seat already booked | SeatAlreadyBookedException |
| Payment fails | Transaction rollback |
| Redis unavailable | Fallback to DB locking |
| Hold expires | Redis TTL auto-release |
| User books another user's held seat | Ownership validation failure |
| Invalid seat ID | ResourceNotFoundException |

---

## Running Locally

### Prerequisites

- Java 21
- Maven
- PostgreSQL
- Redis

### Clone Repository

```bash
git clone https://github.com/your-username/ticket-booking-system.git
cd ticket-booking-system
```

### Start Services

```bash
docker compose up -d
```

### Run Application

```bash
mvn spring-boot:run
```

Application:

```text
http://localhost:8080
```

Swagger:

```text
http://localhost:8080/swagger-ui/index.html
```

---

## Project Progress

| Phase | Description | Status |
|---------|-------------|---------|
| 0 | Project Setup, PostgreSQL, Redis, Docker | ✅ Done |
| 1 | Authentication & Authorization | ✅ Done |
| 2 | Event & Seat Management | ✅ Done |
| 3 | Booking Module & Concurrency Testing | ✅ Done |
| 4 | Redis Soft Lock & Ownership Validation | ✅ Done |
| 5 | Flyway & Swagger Integration | ✅ Done |
| 6 | Resilience4j Circuit Breaker | ⏳ Planned |
| 7 | Idempotency Key Support | ⏳ Planned |
| 8 | Optimistic vs Pessimistic Locking Comparison | ⏳ Planned |
| 9 | Deployment & Final Documentation | ⏳ Planned |

---

## Concepts Demonstrated

### Spring Boot

- Dependency Injection
- Layered Architecture
- DTO Pattern
- Validation
- Exception Handling
- Transaction Management

### Spring Security

- JWT Authentication
- Authorization
- Security Filters
- Refresh Tokens

### Database

- JPA
- Hibernate
- Entity Relationships
- Repository Pattern
- Flyway

### Concurrency

- Race Conditions
- Pessimistic Locking
- Concurrent Testing
- Transaction Isolation

### Redis

- TTL
- SETNX
- Seat Holds
- Ownership Validation
- Soft Locks

---

## Planned Improvements

- Resilience4j Circuit Breaker
- Retry Mechanism
- Idempotency Keys
- Optimistic Locking Comparison
- Docker Deployment
- CI/CD Pipeline
- Monitoring & Metrics

---

**Current Status:** Authentication, Event Management, Seat Management, Booking System, Concurrency Protection, Redis Seat Holds, Flyway, and Swagger are fully implemented and tested.
