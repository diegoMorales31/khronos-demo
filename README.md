# Khronos

Khronos is a REST API for scheduling staff at venues such as restaurants, hotels or event halls. An **owner** registers their establishments and splits each one into **zones** (for example *Terrace*, *Kitchen* or *Main Hall*). The owner then schedules their **workers** into shifts in those zones and assigns them to **events** happening there.

The API stops scheduling mistakes before they are saved. For example, it won't let a worker have two overlapping shifts, and it won't assign a worker to an event that their shift doesn't cover.

## Tech stack

| | |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4 (Web MVC, Data JPA, Validation, Security) |
| Database | Supabase |
| Auth | HTTP Basic, stateless |
| Docs | springdoc OpenAPI / Swagger UI |
| Build | Maven (wrapper included) |

## Domain model

```
User ──(1:1)── Worker ──┬── WorkerShift ──> Zone
 │                      └── WorkerEvent ──> Event ──> Zone
 │
 └─ owns ─> Establishment ──┬── Zone
                            └── Worker
```

| Entity | What it represents |
|---|---|
| **User** | An account that can log in. It has a username, password, name, email, optional phone number and a **role**. |
| **Establishment** | A venue, with a name and a unique address. Each one belongs to one user with the `OWNER` role. |
| **Zone** | An area inside an establishment. Zone names are unique within their establishment. |
| **Worker** | A user with the `WORKER` role who works at one establishment. The worker shares its ID with its user, and it can be marked active or inactive. |
| **WorkerShift** | A block of time a worker spends in a zone: a date, a start time and an end time. |
| **Event** | Something scheduled in a zone at a specific date and time, with a title, an expected number of people and a description. |
| **WorkerEvent** | The assignment of a worker to an event. |

Deleting a parent also deletes everything under it. For example, deleting an establishment removes its zones, workers, shifts and events.

## Roles and permissions

| Role | What they can do |
|---|---|
| `PLATFORM_ADMIN` | Everything. This is the only role that creates users of any role and creates or deletes establishments. |
| `OWNER` | Manages their own establishments: edits them and manages their zones, workers, shifts, events and assignments. They can create users with the `WORKER` role. |
| `WORKER` | Can read their own profile, shifts and event assignments, and see the zones and events of the establishment they work at. They can change their own password. |

Each endpoint checks access with `@PreAuthorize` rules backed by `AccessGuard`. A request for data that belongs to another owner returns **403**.

## Business rules

These rules are enforced in the service layer. Breaking one returns **409 Conflict** with a message explaining why.

**Users**
- Usernames, emails and phone numbers must be unique.
- To change a password, the user must send their current password.
- A platform admin can't be deleted through the API.

**Establishments and zones**
- Two establishments can't share an address.
- An establishment's owner must have the `OWNER` role.
- A zone can't be moved to another establishment.

**Workers**
- Only users with the `WORKER` role can be registered as workers, and each user only once.
- A worker can't move to another establishment while they still have shifts or events.

**Shifts**
- The end time must be after the start time, so a shift can't run past midnight.
- The zone must belong to the worker's establishment.
- A worker's shifts can't overlap on the same day.
- Shifts are created **in batches**: `POST /api/worker-shifts` takes a list. The whole batch is saved in a single transaction, so if any shift is invalid, none of them are saved. The error says which shift failed, for example `Shift [1]: ...`. The list is also checked against itself, so two shifts in the same request can't overlap.

**Events and assignments**
- An event with workers assigned to it can't be moved to another establishment.
- A worker can only be assigned to an event once.
- A worker can only be assigned to an event in their own establishment, and only if one of their shifts covers it: the shift must be in the event's zone, on the event's date, and running at the event's start time.

## API overview

All endpoints are under `/api` and require authentication. The full interactive reference is in Swagger UI (see [Running locally](#running-locally)).

| Resource | Base path | Notes |
|---|---|---|
| Users | `/api/users` | `GET /me` returns the logged-in user. `POST /create?role=...` is for admins only. `POST /create/worker` creates a user with the `WORKER` role. `PUT /{id}/password` changes the password. |
| Establishments | `/api/establishments` | You can also list them by owner: `/owner/{ownerId}`. |
| Zones | `/api/zones` | You can also list them by establishment: `/establishment/{id}`. |
| Workers | `/api/workers` | Identified by their user ID. You can list them by establishment. |
| Worker shifts | `/api/worker-shifts` | `POST` takes a **list**. You can list shifts by owner, worker or zone. |
| Events | `/api/events` | You can list events by zone or establishment. |
| Worker events | `/api/worker-events` | Assigns a worker to an event. You can list assignments by worker or event. |

### Typical flow

1. An admin creates a user with the `OWNER` role (`POST /api/users/create?role=OWNER`) and an establishment for them (`POST /api/establishments`).
2. The owner creates zones (`POST /api/zones`).
3. The owner creates worker accounts (`POST /api/users/create/worker`) and links them to the establishment (`POST /api/workers`).
4. The owner schedules shifts:
   ```json
   POST /api/worker-shifts
   [
     { "workerId": 5, "zoneId": 2, "date": "2026-11-14", "startTime": "18:00", "endTime": "23:00" },
     { "workerId": 6, "zoneId": 2, "date": "2026-11-14", "startTime": "17:00", "endTime": "22:00" }
   ]
   ```
5. The owner creates an event in a zone (`POST /api/events`) and assigns workers whose shifts cover it (`POST /api/worker-events`).

### Error format

Every error comes back in the same shape:

```json
{
  "timestamp": "2026-11-10T10:15:30Z",
  "status": 409,
  "error": "Conflict",
  "message": "Shift [1]: The worker already has a shift that overlaps with ...",
  "path": "/api/worker-shifts",
  "fieldErrors": { "[0].startTime": "must not be null" }
}
```

`fieldErrors` only appears on validation errors.

| Status | When |
|---|---|
| 400 | Validation failed, or the JSON, a parameter or a date/time format is invalid |
| 401 | Credentials are missing or wrong |
| 403 | You aren't allowed to access the resource |
| 404 | The resource or endpoint doesn't exist |
| 409 | A business rule was broken, or the data conflicts with existing records |

Hibernate creates and updates the tables on startup (`ddl-auto=update`).
## Project structure

```
src/main/java/com/moga/khronosdemo
├── config/        OpenAPI and password encoder beans
├── controllers/   REST endpoints and @PreAuthorize rules
├── dtos/          Request DTOs (with validation) and response DTOs
├── entities/      JPA entities and the Role enum
├── exceptions/    Domain exceptions and GlobalExceptionHandler
├── mappers/       Conversions between entities and DTOs
├── repositories/  Spring Data JPA repositories, including the overlap and coverage queries
├── security/      SecurityConfig, AccessGuard and the user-details service
└── services/      Service interfaces and their implementations (business rules)
```
