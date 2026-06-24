# Ticket Booking System

A production-style backend that solves a real concurrency problem: **how do you prevent two users from booking the same seat at the same moment?**

Built with Spring Boot 3, PostgreSQL, and Redis — using a two-layer locking strategy, transactional booking flows, JWT authentication, circuit breakers, idempotency protection, and Flyway-managed schema migrations. Deployed on Railway.

---

## Live Demo
> Note:
> Most endpoints are protected by JWT authentication.
> Open Swagger, login first, copy the access token, then use the **Authorize** button before testing secured APIs.

**Base URL:** `https://ticket-booking-system.up.railway.app`

**Swagger UI:** `https://ticket-booking-system.up.railway.app/swagger-ui/index.html`

| Role  | Email           | Password |
|-------|-----------------|----------|
| Admin | admin@test.com  | 123456   |
| User  | user@test.com   | 123456   |

<img width="1600" height="762" alt="image" src="https://github.com/user-attachments/assets/7193ca6b-024b-41af-9a7a-0a04dd408b97" />

---

## The Core Problem

When a popular event goes live, hundreds of users hit "Book" simultaneously. Without the right locking strategy, two users can read the same seat as `AVAILABLE`, both proceed to payment, and both end up with a booking — a double booking.

This project implements a two-layer defense against that:

```
User A ──┐
User B ──┤──→ Redis Soft Lock (first line) ──→ PostgreSQL Pessimistic Lock (guarantee)
User C ──┘
```

**Redis** stops most concurrent requests early — cheaply, at the cache layer.  
**PostgreSQL** is the source of truth — the lock that actually guarantees correctness.

If Redis goes down, the system falls back to DB locking seamlessly. No double bookings either way.

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                   Spring Boot Application                │
│                                                          │
│   ┌────────┐  ┌─────────┐  ┌────────┐  ┌─────────────┐   │
│   │  auth  │  │  event  │  │  seat  │  │   booking   │   │
│   └────────┘  └─────────┘  └────────┘  └─────────────┘   │
│         ↓           ↓           ↓              ↓         │
│   ┌─────────────────────────────────────────────────┐    │
│   │              PostgreSQL (source of truth)       │    │
│   └─────────────────────────────────────────────────┘    │
│                                                          │
│   ┌─────────────────────────────────────────────────┐    │
│   │         Redis (seat hold / soft lock layer)     │    │
│   └─────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────┘
```

**Why a modular monolith and not microservices?**

Booking, payment, and seat updates must be atomic. Microservices would require distributed transactions (sagas, eventual consistency, message coordination) that add complexity without benefit here. The modular monolith keeps strong transactional guarantees while maintaining clean module boundaries that could be extracted later if needed.

---

## Booking Flow

```
POST /bookings
       │
       ▼
BookingService (@Transactional)
       │
       ├──→ Acquire PESSIMISTIC_WRITE lock on seat row
       │
       ├──→ Check seat status — throw SeatAlreadyBookedException if BOOKED
       │
       ├──→ Verify Redis hold ownership — caller must own the hold
       │
       ├──→ Simulate payment via PaymentService
       │       └──→ Circuit breaker + retry wraps this call
       │
       ├──→ Mark seat BOOKED, save booking record
       │
       └──→ Release Redis hold
              │
              ▼
           Response — transaction commits, DB lock releases
```

---

## Locking Strategy

### Why Pessimistic Locking?

Optimistic locking (`@Version`) is the right choice for low-contention reads. But for a seat booking — where multiple users are contending over the exact same row at the exact same moment — optimistic locking causes failures that get retried repeatedly, degrading into a thundering herd.

```java
// Pessimistic lock — only one transaction touches this row at a time
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Seat s WHERE s.id = :id")
Optional<Seat> findByIdWithLock(@Param("id") Long id);
```

The optimistic locking test in this project deliberately demonstrates the failure mode:

```
OptimisticLockingTest — 8 threads, 1 seat
→ ObjectOptimisticLockingFailureException thrown on concurrent writes
→ Booking count unreliable
→ This is why pessimistic was chosen
```

### Redis Soft Lock (Optimization Layer)

Before a user confirms booking, they hold the seat for 5 minutes:

```
POST /seats/hold
       │
       ▼
SET seat_lock:{eventId}:{seatId}  userId  NX EX 300
```

- `NX` — only set if key doesn't exist (atomic)
- `EX 300` — auto-expires in 5 minutes

If another user tries to hold the same seat, they get a `409 Conflict` immediately — without touching the database.

If Redis is unavailable, the system logs a warning and continues. The DB pessimistic lock still prevents double bookings.

---

## Concurrency Test Results

<img width="1442" height="857" alt="image" src="https://github.com/user-attachments/assets/cd9fd383-81c2-41ec-851e-37c40f6480b4" />


```
Scenario: 8 threads, 1 seat, all booking simultaneously
Tools:    ExecutorService + CountDownLatch(1) — all threads release at once

Result:
  ✓ bookingRepository.countBySeatId(seatId) == 1
  ✓ Exactly 1 booking persisted
  ✓ Remaining 7 requests fail with SeatAlreadyBookedException
  ✓ Passed consistently across @RepeatedTest(3)
```

---

## Resilience

### Circuit Breaker + Retry (Resilience4j)

Payment calls are wrapped with a circuit breaker to prevent cascade failures:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        slidingWindowSize: 5
  retry:
    instances:
      paymentService:
        maxAttempts: 3
        waitDuration: 500ms
```

Aspect ordering is explicitly configured so the circuit breaker wraps the retry — not the other way around. This ensures that retry attempts don't each count as separate circuit breaker failures.

```yaml
  circuitbreaker:
    circuitBreakerAspectOrder: 1
  retry:
    retryAspectOrder: 2
```

When the circuit opens, a fallback immediately returns a payment failure without waiting for timeouts.

### Idempotency Keys

Double-clicks and network retries won't create duplicate bookings. Clients send a UUID header:

```
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

The result is stored in Redis for 24 hours. Identical requests return the cached response.

---

## API Endpoints

The API is documented using OpenAPI 3 and can be explored directly through Swagger UI.

<img width="1600" height="848" alt="image" src="https://github.com/user-attachments/assets/fd892268-4784-4d6b-93b0-c7fba41a9487" />

### Authentication

| Method | Endpoint        | Auth     | Description              |
|--------|-----------------|----------|--------------------------|
| POST   | /auth/register  | Public   | Register new user        |
| POST   | /auth/login     | Public   | Login, returns JWT pair  |
| POST   | /auth/refresh   | Public   | Refresh access token     |
| POST   | /auth/logout    | Bearer   | Invalidate refresh token |

### Events

| Method | Endpoint       | Auth     | Description        |
|--------|----------------|----------|--------------------|
| POST   | /events        | Admin    | Create event       |
| GET    | /events        | Bearer   | List all events    |
| GET    | /events/{id}   | Bearer   | Get event by ID    |

### Seats

| Method | Endpoint                        | Auth   | Description              |
|--------|---------------------------------|--------|--------------------------|
| POST   | /seats                          | Admin  | Add seat to event        |
| GET    | /seats/{id}                     | Bearer | Get seat by ID           |
| GET    | /seats/event/{eventId}          | Bearer | List seats for event     |
| GET    | /seats/event/{eventId}/available| Bearer | List available seats     |
| POST   | /seats/hold                     | Bearer | Hold seat for 5 minutes  |

### Bookings

| Method | Endpoint        | Auth   | Description       |
|--------|-----------------|--------|-------------------|
| POST   | /bookings       | Bearer | Create booking    |
| GET    | /bookings/{id}  | Bearer | Get booking by ID |



---

## Failure Handling

| Scenario                          | Behavior                                      |
|-----------------------------------|-----------------------------------------------|
| Two users book the same seat      | DB pessimistic lock — one succeeds, one fails |
| Seat already booked               | `SeatAlreadyBookedException` (409)            |
| Payment fails                     | Transaction rolls back, seat stays AVAILABLE  |
| Redis unavailable                 | Warning logged, DB lock handles concurrency   |
| Seat hold expires                 | Redis TTL auto-releases after 5 minutes       |
| User books another user's seat    | Ownership validation fails (403)              |
| Duplicate booking request         | Idempotency key returns cached response       |
| Payment service down              | Circuit breaker opens, fallback returns error |

---

## Tech Stack

| Layer               | Technology                    |
|---------------------|-------------------------------|
| Language            | Java 21                       |
| Framework           | Spring Boot 3                 |
| Security            | Spring Security + JWT         |
| Database            | PostgreSQL                    |
| ORM                 | Spring Data JPA / Hibernate   |
| Cache               | Redis                         |
| Migrations          | Flyway                        |
| Resilience          | Resilience4j                  |
| API Docs            | Swagger / OpenAPI 3           |
| Testing             | JUnit 5, ExecutorService      |
| Build               | Maven                         |
| Containerization    | Docker                        |
| Deployment          | Railway                       |

---

## Project Highlights

- JWT Authentication & Authorization
- Refresh Token Support
- Role-Based Access Control (Admin/User)
- Redis Seat Hold System
- Pessimistic Locking
- Optimistic Locking Comparison
- Retry Pattern (Resilience4j)
- Circuit Breaker Pattern (Resilience4j)
- Idempotency Keys
- Flyway Database Migrations
- Dockerized Deployment
- Railway Cloud Deployment
- 80 Automated Tests

## Database Schema

```
roles              users                  events
├── id             ├── id                 ├── id
└── name           ├── email (UNIQUE)     ├── name
                   ├── password           ├── location
                   └── role_id            └── event_time

seats                          bookings
├── id                         ├── id
├── seat_number                ├── user_id
├── status (AVAILABLE/BOOKED)  ├── seat_id (UNIQUE)
├── event_id                   └── booked_at
├── created_at
└── updated_at

refresh_tokens
├── id
├── user_id
├── token
└── expiry_date
```

Schema is version-controlled via Flyway migrations (`V1` through `V6`), including demo data seeding for local and production environments.

---

## Running Locally

Clone and start everything with two commands:

```bash
git clone https://github.com/pranshu-2853/ticket-booking-system.git
cd ticket-booking-system
docker compose up -d
mvn spring-boot:run
```

Local App:

http://localhost:8080

Local Swagger:

http://localhost:8080/swagger-ui/index.html

### Swagger Access

The application is secured using JWT authentication.

After opening Swagger:

1. Login using one of the demo accounts.
2. Copy the access token from the login response.
3. Click **Authorize**.
4. Enter:

```text
Bearer <your_access_token>
```

5. Execute authenticated endpoints.

Only authentication and Swagger endpoints are publicly accessible.

### Environment Variables

```env
DB_URL=jdbc:postgresql://localhost:5432/ticketdb
DB_USER=postgres
DB_PASS=postgres
REDIS_HOST=localhost
JWT_SECRET=your-secret-key
```

See `.env.example` for the full list.

---
## Production Deployment

The application is deployed on Railway using:

- Spring Boot Service
- PostgreSQL Database
- Redis Cache
- Flyway Migrations
- Docker Build Pipeline

Production URL:

https://ticket-booking-system.up.railway.app

Production Swagger:

https://ticket-booking-system.up.railway.app/swagger-ui/index.html

## Testing

```
80 tests — 0 failures — 0 errors

Controller Tests      — auth, booking, seat, event
Service Tests         — booking flow, payment, Redis hold logic
Concurrency Tests     — 8-thread race condition validation
Exception Handler Tests — all custom exception mappings verified
```

## Lessons Learned

Through this project I gained practical experience with:

- Spring Security and JWT authentication
- Database transaction management
- Concurrency handling in high-contention scenarios
- Redis caching and distributed locking concepts
- Resilience patterns using Retry and Circuit Breaker
- Flyway migration management
- Docker containerization
- Railway deployment and environment configuration
- Writing maintainable and testable backend services




