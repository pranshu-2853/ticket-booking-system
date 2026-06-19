# Ticket Booking System

A production-thinking backend built with Spring Boot, demonstrating real concurrency handling, pessimistic locking, Redis soft locks, and JWT-based auth — all in a clean modular monolith architecture.

> **Status: Active Development** — See progress tracker below.

---

## What This Project Demonstrates

- Pessimistic locking to guarantee seat exclusivity under concurrent requests
- Redis soft lock as a UX optimization layer (with graceful DB fallback)
- JWT access token + refresh token auth with DB-backed invalidation
- Modular monolith design — clear module boundaries, no cross-module repository injection
- Concurrency-tested booking flow (ExecutorService + CountDownLatch)
- Flyway-managed schema migrations
- Docker-based local setup (one command)

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Spring Boot Application                │
│                                                         │
│  ┌──────┐  ┌───────┐  ┌───────┐  ┌─────────┐          │
│  │ auth │  │ event │  │ seat  │  │ booking │  ...      │
│  └──────┘  └───────┘  └───────┘  └─────────┘          │
│       ↓          ↓          ↓           ↓               │
│  ┌──────────────────────────────────────────────┐       │
│  │           PostgreSQL (single DB)             │       │
│  └──────────────────────────────────────────────┘       │
│  ┌──────────────────────────────────────────────┐       │
│  │       Redis (soft lock / optimization)       │       │
│  └──────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────┘
```

**Why modular monolith and not microservices?**  
Microservices introduce distributed transaction complexity (two-phase commit, sagas) that is overkill for a system where booking, payment, and seat state must be atomic. A modular monolith gives clean separation of concerns while keeping transactions local and simple.

---

## Booking Flow

```
User → POST /bookings
         │
         ▼
  BookingService (@Transactional)
         │
         ├─→ Acquire PESSIMISTIC_WRITE lock on seat row
         │         (DB blocks other transactions here)
         │
         ├─→ Check seat.status == BOOKED → throw SeatAlreadyBookedException
         │
         ├─→ Simulate payment
         │         │
         │    success ──→ seat.status = BOOKED
         │         │      save Booking (CONFIRMED / SUCCESS)
         │         │      commit → lock released
         │         │
         │    failure ──→ save Booking (CANCELLED / FAILED)
         │                throw PaymentFailedException
         │                rollback → seat stays AVAILABLE
         │                lock released
         ▼
       Response
```

---

## Locking Strategy

### Why Pessimistic Locking?

For a seat booking scenario, multiple users race to book the exact same row. Optimistic locking would let all threads read the seat, then fail at commit time with `ObjectOptimisticLockingFailureException` — resulting in wasted work and requiring retry logic. Pessimistic locking (`SELECT ... FOR UPDATE`) blocks at read time, so only one transaction proceeds. One lock, one winner, no retries needed.

### Redis Soft Lock (Phase 4)

```
User selects seat → POST /seats/hold
        │
        ▼
SET seat_lock:{eventId}:{seatId} userId NX EX 300
        │
   key exists → 409 Conflict (seat held by someone else)
   set success → 200 OK (5-minute hold granted)
        │
        ▼
User proceeds to POST /bookings
        │
        ▼
Lock cleared after booking (success or failure)
```

Redis is the **courtesy layer** — it prevents users from waiting on a DB lock for a seat that's already being booked. The DB pessimistic lock is the **guarantee**. If Redis goes down, the system falls back to DB locking transparently.

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | Public | Register new user |
| POST | `/auth/login` | Public | Login, returns access + refresh token |
| POST | `/auth/refresh` | Public | Get new access token using refresh token |
| POST | `/auth/logout` | Bearer | Invalidate refresh token |
| GET | `/events` | Bearer | List all events |
| GET | `/events/{id}` | Bearer | Get event details |
| POST | `/events` | ADMIN | Create event |
| GET | `/events/{id}/seats` | Bearer | List seats for event |
| POST | `/events/{id}/seats` | ADMIN | Add seats to event |
| POST | `/seats/hold` | Bearer | Soft-lock a seat via Redis (5 min) |
| POST | `/bookings` | Bearer | Book a seat |
| GET | `/bookings/my` | Bearer | View own bookings |

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Database | PostgreSQL 15 |
| Cache / Locks | Redis 7 |
| Auth | JWT (jjwt 0.11.5) + Refresh Token |
| Migrations | Flyway |
| Containerization | Docker + Docker Compose |
| Build | Maven |

---

## How to Run Locally

**Prerequisites:** Docker, Java 21

```bash
# 1. Clone
git clone https://github.com/your-username/ticket-booking-system.git
cd ticket-booking-system

# 2. Start PostgreSQL + Redis
docker-compose up -d

# 3. Run the app
./mvnw spring-boot:run
```

App starts at `http://localhost:8080`

---

## Environment Variables

| Variable | Description | Default (dev) |
|----------|-------------|---------------|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5332/ticketdb` |
| `DB_USER` | DB username | `admin` |
| `DB_PASS` | DB password | `admin` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6380` |
| `JWT_SECRET` | HMAC signing key (min 32 chars) | — |
| `JWT_EXPIRATION` | Access token TTL in ms | `900000` (15 min) |

---

## Project Progress

| Phase | Description | Status |
|-------|-------------|--------|
| 0 | Project setup, Docker, DB schema | ✅ Done |
| 1 | Auth — register, login, refresh, logout | 🔄 In Progress |
| 2 | Event and Seat module (CRUD) | ⏳ Pending |
| 3 | Booking module + concurrency test | ⏳ Pending |
| 4 | Redis soft lock + fallback | ⏳ Pending |
| 5 | Tier 1 refactors (Flyway, Swagger, Resilience4j, Idempotency) | ⏳ Pending |
| 6 | Optimistic vs Pessimistic locking comparison test | ⏳ Pending |
| 7 | Deployment + final README | ⏳ Pending |

---

## Database Schema

```sql
users          — id, email (UNIQUE), password, role_id
roles          — id, name (ROLE_USER / ROLE_ADMIN)
events         — id, name, date, location
seats          — id, event_id, seat_number, status (AVAILABLE/BOOKED)
               — UNIQUE(event_id, seat_number), INDEX(event_id)
bookings       — id, user_id, seat_id (UNIQUE), status (CONFIRMED/CANCELLED),
               — payment_status (SUCCESS/FAILED), INDEX(user_id)
refresh_tokens — id, user_id, token, expiry_date, INDEX(user_id)
```

---

## Failure Scenarios

| Scenario | How It's Handled |
|----------|-----------------|
| Two users race to book same seat | DB pessimistic lock — only one transaction proceeds |
| Payment fails | Transaction rolls back — seat stays AVAILABLE |
| User books an already-booked seat | `SeatAlreadyBookedException` thrown before payment |
| Redis goes down | Try-catch fallback — request passes through to DB lock |
| User closes browser during hold | Redis TTL (5 min) auto-releases the hold |

---

*Deployment link will be added once Phase 7 is complete.*
