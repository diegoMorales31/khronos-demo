# Khronos

Khronos is a REST API for scheduling staff at venues such as restaurants, hotels or event halls. An **owner** registers their establishments and splits each one into **zones** (for example *Terrace*, *Kitchen* or *Main Hall*). The owner then schedules their **workers** into shifts in those zones and assigns them to **events** happening there.

The API stops scheduling mistakes before they are saved. For example, it won't let a worker have two overlapping shifts, and it won't assign a worker to an event that their shift doesn't cover.

Besides what was planned, Khronos records what actually happened: workers clock in and out, owners correct the record with a reason that is kept, and reports compare planned and worked hours, lateness and staff cost per person, with CSV exports.

It also keeps the day-to-day money, entered by hand: the daily cash closing (takings by payment method, cash counted against cash expected), the expenses, and a summary of sales, expenses, staff cost and estimated margin per day, compared with the period before.

And it keeps the inventory of drinks and supplies: suppliers, products, a stock ledger that is only ever added to (purchases, waste, consumption, adjustments and physical counts), orders to suppliers that are received into stock and can create the expense, and an inventory report with CSV exports.

## Tech stack

| | |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4 (Web MVC, Data JPA, Validation, Security, Actuator) |
| Database | PostgreSQL (Supabase in production) |
| Migrations | Flyway |
| Auth | JWT bearer tokens (HS256), valid for 1 hour, stateless |
| Email | Resend |
| Docs | springdoc OpenAPI / Swagger UI |
| Build | Maven (wrapper included), Docker |
| CI | GitHub Actions: the tests run against PostgreSQL on every push and pull request |

## Domain model

<p align="center">
  <img width="466" height="582" alt="image" src="https://github.com/user-attachments/assets/790faaea-37d8-4e96-8b22-af4c2697d1b8" />
</p>
<br>

| Entity | What it represents |
|---|---|
| **User** | An account that can log in. It has a username, password, name, email, optional phone number, a **role** and who created it. |
| **Establishment** | A venue, with a name, a unique address and a time zone (`Europe/Madrid` by default). Each one belongs to one user with the `OWNER` role. |
| **Zone** | An area inside an establishment. Zone names are unique within their establishment. |
| **Worker** | A user with the `WORKER` role who works at one establishment. The worker shares its ID with its user, and it can be marked active or inactive. |
| **WorkerShift** | A block of time a worker spends in a zone: a date, a start time and an end time. It can run past midnight. |
| **Event** | Something scheduled in a zone at a specific date and time, with a title, an expected number of people and a description. |
| **WorkerEvent** | The assignment of a worker to an event. |
| **WorkerRate** | What a worker costs per hour, in euros, from a given day. |
| **TimeEntry** | A clock-in and its clock-out, as recorded. Never deleted through the API, only corrected or voided. |
| **TimeEntryChange** | One row of a time entry's history: who did what, when, why, and the times it left. Rows are never changed. |
| **CashClosing** | The cash closing of one business day at an establishment: takings by payment method, float, cash taken out, cash counted, and the figures worked out from them. A draft until it is closed. |
| **CashClosingChange** | One row of a cash closing's history: who did what, when, why, and the amounts and figures it left. Rows are never changed. |
| **Expense** | Money an establishment spent: business day, category, description, supplier, amount and payment method. |
| **Supplier** | Someone an establishment buys stock from: a name, unique within the establishment, and an optional phone, email and notes. |
| **Product** | Something an establishment keeps in stock: a name, unique within the establishment, a category, the unit it is counted in, an optional minimum, the last known unit cost and an optional usual supplier. It has no stock field: its stock is the sum of its movements. |
| **StockMovement** | One line of a product's stock ledger: type, signed quantity, unit cost, reason, business day, the stock right after it and who recorded it. Rows are never changed or deleted. |
| **PurchaseOrder** | An order of products to one supplier, from draft to received or cancelled, with the expense created when it was received. |
| **PurchaseOrderLine** | One product of an order: quantity ordered, unit cost and quantity received. |

Deleting a parent also deletes everything under it. For example, deleting an establishment removes its zones, workers, shifts and events, and the products and suppliers that have no movements or orders. Time entries, cash closings, expenses, stock movements and purchase orders are the exception: nothing that has them can be deleted, and deleting a shift keeps time entries. See [What deleting does to time entries](#what-deleting-does-to-time-entries), [Deleting and cash records](#deleting-and-cash-records) and [Deleting and inventory records](#deleting-and-inventory-records).

## Roles and permissions

| Role | What they can do |
|---|---|
| `PLATFORM_ADMIN` | Everything. This is the only role that creates users of any role and creates or deletes establishments. |
| `OWNER` | Manages their own establishments: edits them and manages their zones, workers, shifts, events and assignments. They can create users with the `WORKER` role, link to their establishments only the accounts they created, and reset their workers' passwords. They enter, clock out, correct and void their workers' time entries, set their hourly rates and read the reports and CSV exports of their establishments. They also keep their establishments' cash closings and expenses and read the business summary, and keep their inventory: suppliers, products, stock movements, counts, purchase orders and the inventory report. |
| `WORKER` | Can read their own profile, shifts and event assignments, and see the zones and events of the establishment they work at and who is assigned to each event. Listing the establishment's assignments gives them only their own. They see who the owner is but not the owner's email or phone. They can change their own password. They clock themselves in and out, and read their own time entries, their history, their daily report and its CSV. They never see hourly rates or costs, not even their own, nor anything about cash closings, expenses, the business summary or the inventory (**403**). |

Each endpoint checks access with `@PreAuthorize` rules backed by `AccessGuard`. A request for data that belongs to another owner returns **403**.

The paths that only owners and admins use, `/api/cash-closings/**`, `/api/expenses/**`, `/api/reports/establishment/**` (which includes the inventory report and its exports), `/api/workers/{workerId}/rates/**`, `/api/time-entries/establishment/**` and the inventory's `/api/suppliers/**`, `/api/products/**`, `/api/stock-movements/**`, `/api/stock-counts/**` and `/api/purchase-orders/**`, also turn a worker away by URL, before the request is read: a worker gets **403** there even when the body or the parameters are invalid, never a **400** describing them. `/api/reports/worker/**` is not one of them, since workers read their own daily report there.

## Authentication

Clients sign in once and then send a short-lived token with every request.

1. `POST /api/auth/login` with `{ "username": "...", "password": "..." }`. This is the only endpoint that accepts a password.
2. The response holds the token and when it expires:
   ```json
   { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresAt": "2026-11-17T15:42:00Z", "expiresIn": 3600 }
   ```
3. Every other request sends `Authorization: Bearer <accessToken>`.

A wrong password answers **401** "Invalid username or password", whether the account exists, is active or is disabled. Only the right password for a disabled account answers **401** "This account is disabled", so the login reveals nothing about an account to someone who doesn't know its password.

The token is a JWT signed with HS256. It holds only the user id (`sub`), the role, and the `iat` and `exp` times. **It expires one hour after login.** After that the API answers **401** and the client has to sign in again. There is no refresh token in this beta, so sessions stop at the one-hour mark.

The user is reloaded from the database on every request, so some changes take effect before the token expires:

| Change | Effect on existing tokens |
|---|---|
| A worker is deactivated | Rejected immediately with **401** "This account is disabled" |
| The user changes their password, or an admin or owner resets it | Every token issued before the change is rejected, which signs out other devices |
| The user is deleted | Rejected immediately |
| The role or username changes | The token keeps working with the new values, because it identifies the user by id |

HTTP Basic is no longer accepted. If it were, anyone holding a password could skip the one-hour limit.

**Failed logins are limited.** After 5 failed attempts for the same username from the same IP address within 15 minutes, `POST /api/auth/login` answers **429 Too Many Requests** with a `Retry-After` header (in seconds) until those 15 minutes are over, even if the password is right. A successful login clears the count. Each IP address also has its own limit, whatever the usernames: after 50 failed attempts within 15 minutes, every login from it answers 429 until those 15 minutes are over. A successful login does not clear that count. The counts are kept in memory, per instance, and reset on restart; failures that stay under the limits can't push a blocked username or address out of memory. Behind a reverse proxy, set `SERVER_FORWARD_HEADERS_STRATEGY=native` so the API sees the client's IP and not the proxy's; otherwise every client shares the proxy's address and its limit.

| Setting | Environment variable | Default |
|---|---|---|
| Signing secret (at least 32 bytes) | `JWT_SECRET` | none, the app refuses to start without it |
| Token lifetime (ISO-8601 duration) | `JWT_TTL` | `PT1H` |
| Failed logins allowed per username and IP | `LOGIN_MAX_ATTEMPTS` | `5` |
| Failed logins allowed per IP, whatever the usernames | `LOGIN_MAX_ATTEMPTS_PER_IP` | `50` |
| Window for those failures (ISO-8601 duration) | `LOGIN_WINDOW` | `PT15M` |

Generate a secret with `openssl rand -base64 48`. Keep it out of version control. Changing it signs everyone out.

### First platform admin

Only a platform admin can create users, so the first one is created when the app starts. Set all three of `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD` and `APP_ADMIN_EMAIL`. If no user has that username yet, the app creates a `PLATFORM_ADMIN` with that password. If the user already exists it is left exactly as it is, so the variables can stay set. If any of the three is missing, nothing happens. The password must be at least 8 characters and at most 72 bytes, or the app refuses to start. It is never logged.

## Business rules

These rules are enforced in the service layer. Breaking one returns **409 Conflict** with a message explaining why.

**Users**
- Usernames, emails and phone numbers must be unique.
- Passwords need at least 8 characters and at most 72 bytes (BCrypt's limit; accented letters and emoji take more than one byte). Longer ones answer **400**.
- To change a password, the user must send their current password.
- A platform admin can reset the password of any user except another platform admin, and an owner can reset the password of their own workers, without the current password.
- A platform admin can't be deleted through the API.
- An owner can't be deleted while any of their establishments has time entries, and a worker's account can't be deleted while the worker has time entries. See [What deleting does to time entries](#what-deleting-does-to-time-entries).

**Establishments and zones**
- Two establishments can't share an address.
- An establishment's owner must have the `OWNER` role.
- A zone can't be moved to another establishment.
- An establishment with time entries, cash closings, expenses, stock movements or purchase orders can't be deleted.

**Workers**
- Only users with the `WORKER` role can be registered as workers, and each user only once.
- An owner can only link an account they created themselves; otherwise the request gets **403**. Accounts created before this rule existed have no recorded creator, so only a platform admin can link them.
- A worker can't move to another establishment while they still have shifts, events or time entries.
- An inactive worker can't sign in, get new shifts or be assigned to events.
- A worker with time entries can't be deleted; deactivate them instead.

**Shifts**
- A shift whose end time is not after its start time ends on the next day: 18:00 to 02:00 is an 8-hour night shift. See [Night shifts and the business day](#night-shifts-and-the-business-day).
- A shift can't start and end at the same time, and can't last more than 16 hours.
- The zone must belong to the worker's establishment.
- A worker's shifts can't overlap, including shifts on neighbouring days: a Friday shift from 18:00 to 02:00 clashes with a Saturday shift from 01:00. This also holds when two requests arrive at the same time: changes to the same worker's schedule take turns, so the second one sees the first.
- Two changes to the same shift take turns too, and the second one starts from what the first one saved. If the first one gave the shift to a worker the second one didn't expect, the second one answers **409** `The shift was given to another worker while this request was waiting; reload it and try again`.
- Shifts are created **in batches**: `POST /api/worker-shifts` takes a list. The whole batch is saved in a single transaction, so if any shift is invalid, none of them are saved. The error says which shift failed, for example `Shift [1]: ...`. The list is also checked against itself, so two shifts in the same request can't overlap.

**Events and assignments**
- Events are created **in batches**: `POST /api/events` takes a list, possibly across several zones. It's all or nothing: if one item is invalid or its zone doesn't exist, no events are saved.
- A batch of shifts, events or assignments can hold at most 200 items (`BATCH_MAX_SIZE`). A longer list answers **400**.
- An event with workers assigned to it can't be moved to another establishment.
- Assignments are created **in batches**: `POST /api/worker-events` takes a list, so an owner can assign one worker to several events (or several workers at once) in one request. As with shifts, it's all or nothing, and the error names the failing item, for example `Assignment [2]: ...`.
- A worker can only be assigned to an event once, including twice in the same batch.
- A worker can only be assigned to an event in their own establishment, and only if one of their shifts covers it: the shift must be in the event's zone and running at the event's start time. A night shift that started the day before counts, so a Friday shift from 18:00 to 02:00 covers an event at 01:00 on Saturday.
- If a worker's shift is edited or deleted and it no longer covers an event they are assigned to, they are unassigned from that event automatically. The same happens when an event is moved to a time or zone that the worker's shifts don't cover.
- Requests that arrive at the same time take turns: assigning workers to an event, moving or deleting that event, and changing the shifts of its workers. Each one sees what the one before it committed, so an assignment is always checked against the event's current time and zone.

## Time tracking

Shifts say what was planned; time entries say what happened. This part of the API records clock-ins and clock-outs, keeps every correction, and compares planned and worked hours. It does not use location or biometrics.

### Time zone

Each establishment has an IANA time zone, `timeZone`, such as `Europe/Madrid` (the default) or `Atlantic/Canary`. It is optional when creating an establishment, and an update without it keeps the current one. A value that is not a region id (for example `+02:00` or a typo) answers **400** with `fieldErrors.timeZone`.

Clock-ins and clock-outs are stored as UTC instants and returned as ISO-8601 with `Z`, for example `2026-11-20T17:02:00Z`. The establishment's time zone turns them into local dates and times: the business day of an entry, lateness in the reports, and the dates and times in the CSV files. The business day is worked out when an entry is created or corrected, so changing the zone later doesn't move existing entries to another day; set the right zone before workers start clocking in.

### Night shifts and the business day

The shift request did not change: `{ workerId, zoneId, date, startTime, endTime }`. `date` is the **business day**, the day the shift starts. When `endTime` is not after `startTime` the shift ends the next day, so `{ "date": "2026-11-20", "startTime": "18:00", "endTime": "02:00" }` runs until 02:00 on the 21st. Shift responses add `endsNextDay` (true for that shift) and `durationMinutes` (480). Emails show such a shift as `18:00 - 02:00 (+1)`.

- A shift can't last zero minutes or more than 16 hours (**409**).
- Overlaps and event coverage compare the real interval, so shifts and events on neighbouring days are checked against each other.
- The `from` and `to` filters of the shift listings still apply to `date`.
- `durationMinutes` is wall-clock time. On the two nights a year when the clocks change, the real length of a shift that crosses the change differs by an hour.

PostgreSQL keeps each shift's interval in two generated columns, `starts_at` and `ends_at`. Overlaps are prevented by the service under the per-worker lock, not by a database constraint (see [Known limits](#known-limits)).

### Clocking in and out

A worker clocks in with `POST /api/time-entries/clock-in` and out with `POST /api/time-entries/clock-out`. The server's clock sets the time; the client can't send one. A worker can have only one open entry: the requests for one worker take turns, and a partial unique index in the database backs that up.

When a worker clocks in (or an owner enters or corrects an entry), the entry is matched to the worker's shift that is running at the clock-in or starts within the next 3 hours. It then gets that shift's `shiftId` and counts for its date: a clock-in at 00:30 on Saturday during a Friday 18:00-02:00 shift belongs to Friday. With no such shift, `shiftId` is null and the business day is the local date of the clock-in. Creating shifts (one or a batch) matches the worker's entries that have no shift yet and clock in within a new shift's window, and moves them to its date: if a worker clocks in at 00:30 on Saturday and the owner enters the Friday 18:00-02:00 shift afterwards, the entry gets that shift and counts for Friday. An entry in the window of two new shifts goes to the one that starts first. Voided entries are left as they are.

Each entry has a `status`: `OPEN` (no clock-out yet), `CLOSED` or `VOIDED`. `workedMinutes` is the whole minutes between clock-in and clock-out minus `breakMinutes`, and null while open. `isStale` is true when an entry has been open for more than 16 hours, which usually means a forgotten clock-out. The owner can clock the worker out (see below). Neither of them can clock out an entry that has been open for more than 24 hours, because no entry lasts longer than that: the owner corrects it with the real clock-out time instead.

| Request | When it answers 409 |
|---|---|
| Clock in | `You already have an open time entry`, `Worker is not active` (an inactive worker can't sign in, so in practice they get 401), `The time entry overlaps another time entry of this worker` |
| Clock out | `You have no open time entry`, `This time entry has been open for more than 24 hours; ask a manager to correct it`, `The break cannot be longer than the time between clock-in and clock-out`, `The saved break is longer than the time since clock-in; send breakMinutes with the clock-out` (see below) |

A clock-out without `breakMinutes` keeps the entry's break. An open entry's break can never be longer than the time since its clock-in (see [Corrections and history](#corrections-and-history)), so the kept break always fits. Only an open entry saved before that rule can have a longer one; its clock-out without `breakMinutes` answers **409** `The saved break is longer than the time since clock-in; send breakMinutes with the clock-out`, the entry stays open, and the same request with `breakMinutes` closes it.

### Corrections and history

The establishment's owner or an admin can:

- enter an entry for a worker with `POST /api/time-entries`: without `clockOut` it clocks them in now, with it it records a past shift (`source` is `MANAGER`). An entry left open can't have a `breakMinutes` longer than the time since its clock-in, so that clocking it out later without a break never fails; the same goes for a correction that leaves the entry open;
- clock a worker out with `POST /api/time-entries/{id}/clock-out`, for example when they left without doing it. As when the worker does it, the clock-out is the server's time; `breakMinutes` (0 to 600) is optional and, when left out, the entry keeps its break. The entry keeps its `source` and `isEdited` stays false, because no time was changed;
- correct an entry with `PUT /api/time-entries/{id}`, which sets `isEdited` and matches it to a shift again;
- void it with `PUT /api/time-entries/{id}/void`. A voided entry stays, but listings leave it out unless `includeVoided=true`, and reports never count it.

Each of these needs a `reason` of 3 to 255 characters. Entries are never deleted through the API. Every creation, clock-out, correction and void adds a row to the entry's history (`GET /api/time-entries/{id}/history`, oldest first) with the action, who did it, when, the reason, and the clock-in, clock-out and break the entry had right after. A clock-out by the owner is a `CLOCKED_OUT` row with the owner as the author and their reason. History rows can't be changed: a database trigger rejects updates.

A clock-out by the owner answers **409** with `The time entry is already clocked out`, `A voided time entry cannot be clocked out`, `The break cannot be longer than the time between clock-in and clock-out`, `The saved break is longer than the time since clock-in; send breakMinutes with the clock-out` or, for an entry open for more than 24 hours, `This time entry has been open for more than 24 hours; correct it with PUT /api/time-entries/{id} and the real clock-out time` (with the entry's id).

Manual entries and corrections answer **409** with one of: `Clock-in cannot be in the future`, `Clock-out cannot be in the future` (both allow one minute of clock difference), `Clock-out must be after clock-in`, `A time entry cannot last more than 24 hours`, `The break cannot be longer than the time between clock-in and clock-out`, `The break cannot be longer than the time since clock-in` (an entry without `clockOut`), `The time entry overlaps another time entry of this worker` (voided entries don't count), `A voided time entry cannot be corrected` and `The time entry is already voided`. An owner can enter past days for an inactive worker.

### What deleting does to time entries

- **A shift**: its entries stay. They lose the link (`shiftId` becomes null) and keep their business day. Editing a shift so that an entry no longer fits it (another worker, or a clock-in outside the new window) unlinks that entry the same way; entries that still fit follow the shift's date.
- **A worker, their account, an establishment or its owner**: refused with **409** while there are time entries that the delete would take with it, voided ones included. Deleting an establishment deletes its workers, and deleting an owner deletes their establishments, so those count too. **Deactivate the worker instead** (`PUT /api/workers/{id}/active`): they can't sign in or be scheduled, and their entries, history and rates stay. The database refuses these deletes as well (`ON DELETE RESTRICT` on the entries' worker and establishment and on the history's entry), so they can't happen outside the API either. Without time entries, the delete works as before: a worker goes with their account, shifts, assignments and rates.

  | Request | Message of the 409 |
  |---|---|
  | `DELETE /api/workers/{id}` | `Cannot delete a worker who has time entries; deactivate them instead (PUT /api/workers/{id}/active) to keep their time entries` |
  | `DELETE /api/users/{id}` of a worker | `Cannot delete the account of a worker who has time entries; deactivate the worker instead (PUT /api/workers/{id}/active) to keep their time entries` |
  | `DELETE /api/users/{id}` of an owner | `Cannot delete an owner whose establishments have time entries; deactivate their workers instead to keep the time entries` |
  | `DELETE /api/establishments/{id}` | `Cannot delete an establishment that has time entries; deactivate its workers instead to keep the time entries` |
- **The account that made a change**: the change stays, with the author's name; only the link to the account is cleared.

### Hourly rates

A rate is what a worker costs per hour, in euros, from `effectiveFrom` on. A past rate is never edited: a pay change is a new rate from a later day, and a second rate for the same day answers **409** `The worker already has a rate effective from 2026-11-01`. The rate for a day is the one with the latest `effectiveFrom` on or before it. A rate can only be deleted before it takes effect, while its `effectiveFrom` is after today in the establishment's time zone: deleting one that is already in force would silently change the cost of days already worked, so it answers **409** `Cannot delete a rate that has already taken effect; add a new rate from the day the change applies instead`. Only the worker's owner or an admin can read or change rates.

### Reports

Both reports need `from` and `to` (`YYYY-MM-DD`, both included) and cover at most 366 days. A missing date, `from` after `to`, or a longer range answers **400**.

- `GET /api/reports/establishment/{id}/hours` gives one row per worker: planned minutes (the shifts dated in the range), worked minutes (closed, not voided entries whose business day is in the range), the difference, the number of shifts and entries, late shifts (`lateCount`: shifts whose first entry, leaving out voided ones, clocked in more than 10 minutes after the shift started; a worker who steps out and clocks in again during the same shift is not late twice), missed shifts (ended with no entry matched to them), entries without a shift, open entries, the rate in force today, and the planned and actual cost. Totals add up every row. Inactive workers only appear when they have something in the range.
- `GET /api/reports/worker/{id}/days` lists each day with a shift or an entry, with its shifts and entries and a `status`, taking the first that applies: `OPEN` (an entry is still open), `MISSED` (one of the day's shifts has ended with no entry matched to it, even if another shift of the day was worked), `UNPLANNED` (an entry is not matched to any shift, as on a day without shifts), `LATE` (the first entry of one of the day's shifts clocked in late), `UPCOMING` (a shift hasn't ended and no entry is matched to it yet), otherwise `OK`. Each shift and entry counts as in the hours report, so a day is never `OK` while that report counts a missed shift or an entry without a shift on it. A worker can read their own, without costs.

A cost is each day's minutes times the rate in force that day, in euros rounded to cents. It is null when the worker has no rate for some of those minutes, because a partial sum would understate it. Totals add up the known costs, so a total can leave someone out: in the hours report, `totals.costPartial` (boolean) is true when `totals.actualCost` is known but at least one worker who worked in the range has an unknown cost, and `totals.workersWithoutCost` (number) says how many workers who worked in the range have an unknown cost, whether or not `actualCost` is null. A worker whose only entry is still open has no minutes yet and doesn't count. Each report runs the same few queries however many workers and days it covers.

### CSV exports

`hours.csv` has the columns of the hours report, one row per worker. `time-entries.csv` is the time-tracking record: one row per entry with the person, business day, clock-in, clock-out, break, hours worked, who entered it, whether it was corrected and its status; it leaves out voided entries unless `includeVoided=true`.

The files open directly in a spreadsheet set to Spanish: UTF-8 with a byte order mark, `;` between cells, Spanish headers, dates as `dd/MM/yyyy`, times in the establishment's time zone and amounts with a decimal comma. A cell typed by a user that starts with `=`, `+`, `-` or `@` gets a leading `'`, so a spreadsheet won't run it as a formula.

### Settings

| Setting | Environment variable | Default |
|---|---|---|
| A shift whose first clock-in is later than this after it starts is late | `LATE_THRESHOLD` | `PT10M` |
| A clock-in up to this long before a shift starts is matched to it | `SHIFT_MATCH_WINDOW` | `PT3H` |
| An entry open longer than this is stale | `STALE_ENTRY_AFTER` | `PT16H` |

### Known limits

- The database doesn't stop overlapping shifts by itself. An exclusion constraint on `(worker_id, tsrange(starts_at, ends_at))` would, but adding it would make the migration fail on a database that already holds overlapping shifts written before the per-worker lock existed. The service check under that lock covers every request that goes through the API.
- This is a record of what workers and owners entered. It is not certified as meeting any particular legal requirement for working-time records.
- The append-only records (the time entries' history, the cash closings' history and the stock movements) are protected by row-level triggers (`BEFORE UPDATE`, and `OR DELETE` for stock movements, `FOR EACH ROW`): they cover the application and any SQL that changes or deletes rows, but a row trigger doesn't fire on `TRUNCATE`, which empties a whole table at once. The integration tests rely on that to clean the database between tests. Stopping `TRUNCATE` in production is a matter of the database role's privileges, not of a trigger: the role the application connects with should not have the `TRUNCATE` privilege on these tables, nor own them, since a table's owner can always truncate it. Flyway can run the migrations with a separate owner role (`spring.flyway.user` and `spring.flyway.password`).

## Cash, expenses and the business summary

The manager enters the day's money by hand; Khronos works out the rest and shows it per day and per period. This is an internal management record. It is not a point-of-sale system, an invoice, a tax record or a record that meets any fiscal or accounting requirement, and it doesn't split VAT.

Amounts are euros (`EUR`) with two decimals: requests accept at most two decimals and at most 99,999,999.99, and responses always have two, for example `12.50` and `0.00`. They are stored as `numeric(12,2)` and computed as `BigDecimal`, never as floating point. Amounts entered are zero or more (an expense more than zero); differences and margins can be negative. Dates are business days, `YYYY-MM-DD`, as for shifts and time entries: a night that ends at 02:00 belongs to the day before.

### Cash closing

One closing per establishment and business day. The request (`POST` to create, `PUT` to edit):

```json
{
  "establishmentId": 1, "businessDate": "2026-11-20",
  "openingFloat": 150.00, "cashSales": 820.50, "cardSales": 1300.25, "otherSales": 0,
  "cashWithdrawals": 200.00, "countedCash": 748.00, "tips": 35.50, "notes": "Busy night"
}
```

`otherSales` (Bizum, vouchers...) and `cashWithdrawals` (cash taken out of the till) are 0 when left out; `tips` (informative, not sales) and `notes` (at most 500 characters) are optional. `countedCash` is optional too while the closing is a draft, so it can be saved before the till is counted: until it is set, `countedCash` and `difference` are `null` in the response (don't send 0, which would show a false difference). A `PUT` replaces every amount, so an edit that leaves `countedCash` out clears it. A day can only be closed once its cash is counted. `establishmentId` and `businessDate` are only read when the closing is created: an edit ignores them. The response adds what Khronos works out:

| Field | How |
|---|---|
| `cashExpenses` | The day's expenses paid in cash |
| `totalSales` | `cashSales + cardSales + otherSales` |
| `expectedCash` | `openingFloat + cashSales - cashExpenses - cashWithdrawals` |
| `difference` | `countedCash - expectedCash`: negative when cash is missing; `null` while `countedCash` is `null` |

A closing is a `DRAFT` until it is closed. A draft can be edited and its figures are worked out again on every read, so an expense entered later shows up. `PUT /{id}/close` stores the figures as they are and the status becomes `CLOSED`: from then on they don't change. A difference larger than 5.00 EUR either way (`CASH_DIFFERENCE_THRESHOLD`) needs a `differenceReason` (at most 255 characters) to close. `PUT /{id}/reopen` with a `reason` (3 to 255 characters) makes it a draft again; the stored figures, the reason for the difference and who closed it are cleared from the closing and stay in its history.

Every creation, edit, close and reopen adds a row to the closing's history (`GET /{id}/history`, oldest first) with the action (`CREATED`, `UPDATED`, `CLOSED`, `REOPENED`), who did it, when, the reason (the `differenceReason` when closing, the `reason` when reopening) and the totals right after (`countedCash` and `difference` are `null` in the rows where the cash wasn't counted yet). History rows can't be changed: a database trigger rejects updates.

Each of these 409s carries a stable `code` (see [Error format](#error-format)); clients should rely on it rather than on the message.

| Request | When it answers 409 | `code` |
|---|---|---|
| `POST` | `A cash closing already exists for 2026-11-20 at this establishment; edit that one instead` | `CASH_CLOSING_EXISTS`, with `details.date` |
| `POST` | `The business date cannot be in the future` (today is the establishment's local date) | `FUTURE_DATE` |
| `PUT /{id}` | `A closed cash closing cannot be edited; reopen it first` | `CASH_CLOSING_CLOSED` |
| `PUT /{id}/close` | `The cash closing is already closed` | `CASH_CLOSING_CLOSED` |
| `PUT /{id}/close` | `The cash has not been counted; enter countedCash before closing the day` | `CASH_NOT_COUNTED` |
| `PUT /{id}/close` | `The cash difference of -7.50 EUR is larger than the 5.00 EUR allowed without a reason; send a differenceReason to close the day` | `DIFFERENCE_REASON_REQUIRED` |
| `PUT /{id}/reopen` | `The cash closing is not closed` | `CASH_CLOSING_NOT_CLOSED` |
| `DELETE /{id}` | `A closed cash closing cannot be deleted; reopen it to correct it instead` | `CASH_CLOSING_CLOSED` |
| `DELETE /{id}` | `A cash closing that has been closed before cannot be deleted; correct it and close it again instead` | `CASH_CLOSING_WAS_CLOSED` |

Only a draft that was never closed can be deleted, and its history goes with it. A day that was closed once stays on record: it can be reopened and corrected, not deleted.

Requests on the same establishment's cash take turns: every change to a closing or an expense first locks the establishment's row. Two closings sent at once for the same day can't both be saved (the second one gets the 409 above), and a cash expense can't slip into a day while it is being closed. The unique constraint on establishment and business day backs this up in the database, and its violation answers the same 409. The row lock is PostgreSQL's `FOR NO KEY UPDATE`, so it doesn't hold up a clock-in or anything else that only inserts rows pointing at the establishment.

### Expenses

`{ "establishmentId": 1, "date": "2026-11-20", "category": "SUPPLIES", "description": "Beer kegs", "supplier": "Mahou", "amount": 120.50, "paymentMethod": "CASH", "notes": null }`

- `category`: `SUPPLIES` (food and drink stock), `RENT`, `UTILITIES` (electricity, water, gas, internet), `PAYROLL` (wages and social security), `MAINTENANCE`, `MARKETING`, `TAXES`, `OTHER`.
- `paymentMethod`: `CASH`, `CARD`, `TRANSFER`, `OTHER`.
- `description` 1 to 150 characters, `supplier` at most 100, `notes` at most 500, `amount` more than 0. The date can't be later than today in the establishment's time zone (**409** `The expense date cannot be in the future`, `code` `FUTURE_DATE`). `establishmentId` is only read on creation.
- The listing is newest first, then by id, with optional `from`, `to` and `category` filters. Like the cash closings' listing, it covers at most 366 days (see [Listing ranges](#listing-ranges)).
- The response has the fields sent, plus `id`, `establishmentId`, `createdAt`, `createdByName` and `purchaseOrderId`: the [purchase order](#purchase-orders-and-receiving) whose receipt created the expense, or `null` for one entered by hand. The listing finds them all in one more query, however many expenses there are.
- An expense created by receiving a purchase order is that order's record of what was paid, so it can't be edited: **409** `This expense was created when purchase order #12 was received and cannot be edited; to correct it, delete it and enter it again`, with `code` `EXPENSE_FROM_PURCHASE_ORDER` and the order's id in `details.purchaseOrderId` (`"12"`). Clients can tell it apart beforehand by its `purchaseOrderId`. It can still be deleted, under the closed-day rule below; the order keeps its movements and its `expenseId` becomes null.

Cash expenses come out of the till, so while a day's closing is `CLOSED` its cash expenses can't be added, changed, moved in or out, or deleted: **409** `The cash closing of 2026-11-20 is closed; reopen it to add, change or delete cash expenses of that day`, with `code` `CASH_DAY_CLOSED` and the closed day in `details.date`. An expense counts as cash if it was paid in cash before the change or will be after it. Other payment methods are never locked.

### Listing ranges

`GET /api/cash-closings/establishment/{id}` and `GET /api/expenses/establishment/{id}`, and the inventory's `GET /api/stock-movements/establishment/{id}` and `GET /api/purchase-orders/establishment/{id}`, take optional `from` and `to` (`YYYY-MM-DD`, both included) but never return more than 366 days, so a listing without dates doesn't bring back the whole history. For stock movements the days are their business `date`; for purchase orders, the days they were created on in the establishment's time zone. The answer keeps its shape, a plain list, and the other filters still apply within the range:

| Sent | Range listed |
|---|---|
| Neither | The 366 days up to today in the establishment's time zone: `today - 365` to `today` |
| Only `from` | `from` to `from + 365` |
| Only `to` | `to - 365` to `to` |
| Both | `from` to `to`; **400** `The date range cannot be longer than 366 days` if that is more than 366 days, and **400** `'from' must not be after 'to'` as before |

To see older records, send the dates of the range you want; an order created more than 366 days ago no longer shows up without dates, whatever its status. Without dates the establishment has to exist, so an admin gets **404** for an id that doesn't; with dates, such a listing is just empty.

### Business summary

`GET /api/reports/establishment/{id}/summary?from&to` (both required, at most 366 days) returns:

- `totals`: `sales`, `cashSales`, `cardSales`, `otherSales` and `tips` from the closings of the range, drafts included; `expenses` (all of them) and `payrollExpenses` (the `PAYROLL` ones); `laborCost`, `laborCostPartial` and `workersWithoutCost` (as `costPartial` and `workersWithoutCost` in the hours report) and `plannedLaborCost`; `grossMargin` and `laborCostSource`; `laborCostPercent`; `cashDifference` (the differences of the `CLOSED` days added up) and `draftCashDifference` (what the drafts' differences add up to right now; provisional, since a draft's figures still change, and a draft whose cash hasn't been counted adds nothing); and `closedDays`, `draftDays` and `missingClosingDays`.
- `days`: one row for every day of the range, earliest first, with `sales`, `expenses`, `laborCost`, `margin`, `closingStatus` and `cashDifference` (null without a closing, or while its cash hasn't been counted).
- `expensesByCategory`: the categories with expenses, largest first.
- `previous`: `sales`, `expenses`, `laborCost`, `grossMargin` and `laborCostSource` of the period of the same length right before `from`.

**How the estimated margin is worked out.** `laborCost` is the staff cost from the hours worked: the actual cost of the [hours report](#reports), minutes of the entries that count times each worker's rate in force that day, adding up the workers whose cost is known. It is null when no worker's cost is known (nobody has a rate). When it is known but leaves out someone who worked without a rate for some of their days, `laborCostPartial` is true and `workersWithoutCost` counts them: the margin then takes off a staff cost that is too low. `plannedLaborCost` does the same with the shifts. Then:

```
grossMargin = sales - (expenses - payrollExpenses) - staff cost
staff cost  = laborCost        when laborCost is known      (laborCostSource "HOURS")
            = payrollExpenses  when laborCost is null       (laborCostSource "PAYROLL_EXPENSES")
```

With a known `laborCost`, `PAYROLL` expenses are left out because the staff cost already counts the pay, so it isn't counted twice. When `laborCost` is null (no worker has a rate), the `PAYROLL` expenses stand in for the staff cost, so pay is still taken off and the margin is `sales - expenses`: 250 of sales, 40 of supplies and 1000 of payroll give a margin of -790, not 210. `laborCostSource` says which staff cost the margin took off. Each day's `margin` and the `previous` period's `grossMargin` are worked out the same way with their own figures: a day whose `laborCost` is null takes off that day's `PAYROLL` expenses, and `previous.laborCostSource` follows `previous.laborCost`. A day's `laborCost` counts the same workers as the total and is rounded to cents per day, so the days can differ from the total by a rounding cent. `laborCostPercent` is `laborCost / sales x 100` with one decimal, null when there are no sales or `laborCost` is null. The margin is an estimate: it doesn't include taxes, depreciation or anything not entered as an expense.

`closingStatus` is `CLOSED` or `DRAFT` when the day has a closing; `MISSING` when it has none, is before today in the establishment's time zone and had a shift or a time entry that counts; otherwise `NONE`. Today is never `MISSING`, since its closing isn't due yet. `missingClosingDays` counts the `MISSING` days.

The summary runs the same seven queries whatever the length of the range.

### Cash and summary CSV exports

In the same format as the [time-tracking exports](#csv-exports) (UTF-8 with a BOM, `;`, CRLF, Spanish headers, `dd/MM/yyyy`, decimal commas, user-typed text that can't run as a formula), with `from` and `to` required:

| Export | File | Columns |
|---|---|---|
| `GET /api/reports/establishment/{id}/cash-closings.csv` | `cierres-caja_{from}_{to}.csv` | One row per closing, earliest first: `Día de servicio;Estado;Fondo inicial (EUR);Ventas en efectivo (EUR);Ventas con tarjeta (EUR);Otras ventas (EUR);Ventas totales (EUR);Gastos en efectivo (EUR);Retiradas de efectivo (EUR);Efectivo esperado (EUR);Efectivo contado (EUR);Descuadre (EUR);Motivo del descuadre;Propinas (EUR);Notas;Cerrado el;Cerrado por`. `Estado` is `Borrador` or `Cerrado`; a draft whose cash hasn't been counted leaves `Efectivo contado` and `Descuadre` empty. |
| `GET /api/reports/establishment/{id}/expenses.csv` | `gastos_{from}_{to}.csv` | One row per expense, oldest first: `Fecha;Categoría;Descripción;Proveedor;Importe (EUR);Medio de pago;Notas;Registrado por`, with the category and payment method in Spanish. |
| `GET /api/reports/establishment/{id}/summary.csv` | `resumen_{from}_{to}.csv` | One row per day: `Fecha;Ventas (EUR);Gastos (EUR);Coste de personal (EUR);Margen (EUR);Estado del cierre;Descuadre (EUR)`. `Estado del cierre` is `Cerrado`, `Borrador`, `Falta el cierre` or `Sin cierre`; an unknown staff cost is left empty, and that day's `Margen` then takes off its payroll expenses, as in the summary; so is `Descuadre` without a closing or while its cash hasn't been counted. |

### Deleting and cash records

Cash closings (drafts included) and expenses are the establishment's financial record, so they are kept like time entries:

| Request | Message of the 409 |
|---|---|
| `DELETE /api/establishments/{id}` | `Cannot delete an establishment that has cash closings or expenses; keep it to keep its financial records` |
| `DELETE /api/users/{id}` of an owner | `Cannot delete an owner whose establishments have cash closings or expenses; keep the owner to keep those financial records` |

The database refuses these deletes as well (`ON DELETE RESTRICT` on the closings' and expenses' establishment and on the history's closing). Deleting the account that entered an expense, closed a day or changed a closing keeps the record with that person's name and only clears the link.

### Cash closing, expense and summary endpoints

All of them are for the establishment's owner or an admin; a worker gets **403**, before any validation, and so does an owner for another owner's establishment, also when it is named in a request body.

| Method and path | Body | Answer |
|---|---|---|
| `GET /api/cash-closings/establishment/{id}?from&to` | | **200** closings, latest day first; `from` and `to` optional, at most 366 days (see [Listing ranges](#listing-ranges)) |
| `GET /api/cash-closings/{id}` | | **200** the closing |
| `POST /api/cash-closings` | the closing | **201** the closing, `DRAFT` |
| `PUT /api/cash-closings/{id}` | the closing | **200** the closing |
| `PUT /api/cash-closings/{id}/close` | `{ "differenceReason": "..." }`, optional | **200** the closing, `CLOSED` |
| `PUT /api/cash-closings/{id}/reopen` | `{ "reason": "..." }` | **200** the closing, `DRAFT` |
| `DELETE /api/cash-closings/{id}` | | **204** |
| `GET /api/cash-closings/{id}/history` | | **200** the changes, oldest first |
| `GET /api/expenses/establishment/{id}?from&to&category` | | **200** expenses, newest first; at most 366 days, as for closings |
| `POST /api/expenses` | the expense | **201** the expense |
| `PUT /api/expenses/{id}` | the expense | **200** the expense |
| `DELETE /api/expenses/{id}` | | **204** |
| `GET /api/reports/establishment/{id}/summary?from&to` | | **200** the summary |
| `GET /api/reports/establishment/{id}/cash-closings.csv?from&to` | | **200** `text/csv` file |
| `GET /api/reports/establishment/{id}/expenses.csv?from&to` | | **200** `text/csv` file |
| `GET /api/reports/establishment/{id}/summary.csv?from&to` | | **200** `text/csv` file |

A closing looks like this:

```json
{
  "id": 7, "establishmentId": 1, "businessDate": "2026-11-20", "status": "CLOSED",
  "openingFloat": 150.00, "cashSales": 820.50, "cardSales": 1300.25, "otherSales": 0.00,
  "cashWithdrawals": 200.00, "countedCash": 748.00, "tips": 35.50, "notes": "Busy night",
  "cashExpenses": 20.00, "totalSales": 2120.75, "expectedCash": 750.50, "difference": -2.50,
  "differenceReason": null, "closedAt": "2026-11-21T01:10:00Z", "closedByName": "Lucía Pérez",
  "createdAt": "2026-11-20T23:55:00Z", "updatedAt": "2026-11-21T01:10:00Z"
}
```

### Cash settings

| Setting | Environment variable | Default |
|---|---|---|
| Largest difference, in euros either way, a day can be closed with without a reason | `CASH_DIFFERENCE_THRESHOLD` | `5.00` |

## Inventory

Khronos keeps the stock of a bar's drinks and supplies, entered by hand. **There is no automatic stock deduction from sales**: Khronos is not connected to a point-of-sale system, so what is sold or used is entered as consumption, or shows up in the next physical count. There are no recipes, no conversions between units, no batches or expiry dates, one store per establishment and no transfers between establishments.

Quantities are in the product's unit, zero or more unless said otherwise, with up to three decimals, so half a bottle can be counted: they are stored as `numeric(12,3)`, computed as `BigDecimal` and always returned with three decimals (`2.500`). A movement records at most 999,999,999.999 either way, and a product's stock can be at most 999,999,999,999.999 either way (`stockAfter` is `numeric(15,3)`): a request whose movement or resulting stock would go past them answers **400** with `fieldErrors` on the field that caused it, and records nothing. Money is as in the [cash section](#cash-expenses-and-the-business-summary): euros, at most two decimals in requests, always two in responses. A value is a quantity times a unit cost, rounded half up to cents.

### Suppliers

`{ "establishmentId": 1, "name": "Mahou", "phone": "910 000 000", "email": "pedidos@mahou.test", "notes": null }`

`name` 1 to 100 characters, `phone` at most 30, `email` a valid address, `notes` at most 500. A name is unique within its establishment whatever its case: **409** `A supplier named 'MAHOU' already exists at this establishment` (code `SUPPLIER_NAME_TAKEN`), backed by a unique index on `lower(name)`. The listing is by name. `establishmentId` is only read on creation. Deleting a supplier clears it from the products that had it as their usual supplier; a supplier with purchase orders can't be deleted: **409** `Cannot delete a supplier that has purchase orders` (`SUPPLIER_HAS_ORDERS`), also when the order is being created at the same moment as the delete.

### Products

`{ "establishmentId": 1, "name": "Ron Barceló", "category": "SPIRITS", "unit": "BOTTLE", "minStock": 2, "unitCost": 11.50, "supplierId": 3 }`

- `category`: `SPIRITS`, `BEER`, `WINE`, `SOFT_DRINKS` (soft drinks and water), `COFFEE`, `FOOD`, `SUPPLIES` (napkins, ice, cleaning), `OTHER`.
- `unit`, how the product is counted: `BOTTLE`, `UNIT`, `KG`, `L`, `BOX`, `KEG`. It can't change once the product has movements or is on a purchase order line, whatever the order's status (a draft or ordered line was counted in the old unit, and would come in under the new one): **409** `The unit of a product with stock movements or purchase order lines cannot be changed` (`PRODUCT_UNIT_LOCKED`).
- `name` 1 to 100 characters, unique within the establishment whatever its case (**409** `PRODUCT_NAME_TAKEN`). `minStock` and `unitCost` are optional, zero or more. `supplierId` must be a supplier of the same establishment (**409** `The supplier belongs to another establishment`, `OTHER_ESTABLISHMENT`).

A product has no stock field. The response works out:

| Field | How |
|---|---|
| `currentStock` | The sum of all its movements |
| `stockValue` | `currentStock × unitCost`; null without a cost, `0.00` when the stock is 0 or less |
| `isLowStock` | `minStock` is set and `currentStock <= minStock` |

`PUT /{id}/active` with `{ "active": false }` deactivates a product: it keeps its history but takes no new movements, counts or order lines (**409** `Product 'Ron Barceló' is inactive; activate it first`, `PRODUCT_INACTIVE`), and listings leave it out unless `includeInactive=true`. A product can only be deleted while nothing was recorded or ordered for it; otherwise **409** `Cannot delete a product that has stock movements or purchase order lines; deactivate it instead (PUT /api/products/{id}/active) to keep its history` (`PRODUCT_HAS_HISTORY`). Listings are by category, in the order above, then by name.

**How the stock is worked out.** The stock is never stored: every listing adds up the movements of all the establishment's products in one grouped query, so it runs the same two queries however many products and movements there are. That query reads only an index on `(establishment_id, product_id)` that includes `quantity` (`V15`), without visiting the movements' rows, but it still adds up the whole history, so its cost grows with the number of movements: this is a known limit. Keeping a running total on the product row would save that sum, but it would be a second copy of the truth that has to be kept right on every path; with the ledger as the only source, there is nothing to drift. If the sums ever became slow, a total maintained in the same transaction and under the same lock as each movement would be the next step.

### Stock movements, and why they are never edited

`POST /api/stock-movements` with `{ "productId": 5, "type": "WASTE", "quantity": 1.5, "unitCost": null, "reason": "Se cayó una botella", "date": "2026-11-20" }`.

| `type` | What it is | `quantity` sent | Stored as |
|---|---|---|---|
| `PURCHASE` | Stock in, bought | more than 0 | positive |
| `WASTE` | Breakage, out of date, given away, stolen | more than 0 | negative |
| `CONSUMPTION` | Used or sold, entered by hand | more than 0 | negative |
| `ADJUSTMENT` | A manual correction | signed, not 0 | as sent |
| `COUNT` | The difference a physical count found | only recorded by a [count](#stock-counts) | the difference |

- `reason` is required, 3 to 255 characters, for `WASTE` and `ADJUSTMENT`, and optional (at most 255) otherwise.
- `unitCost` can only be sent with a `PURCHASE`, and then becomes the product's cost. Each movement keeps the cost it is valued at: the purchase's, or the product's at that moment (null if it has none). `value` is `|quantity| × unitCost`, never negative; the [stock-movements CSV export](#inventory-report-and-csv-exports) gives it the quantity's sign instead.
- `date` is the business day; it defaults to today in the establishment's time zone and can't be later (**409** `The movement date cannot be in the future`, `FUTURE_DATE`).
- The stock may go below zero; that happens when a purchase wasn't entered, and isn't an error.
- `stockAfter` is the product's stock right after the movement was recorded.

These rules answer **400** with `fieldErrors`: `type` (`COUNT is only recorded by a stock count (POST /api/stock-counts)`), `quantity` (`must be greater than 0`, `must not be 0`, and `would leave a stock of 1000000000000.000; a product's stock can be at most 999999999999.999 either way` when the stock after it wouldn't fit), `reason` (`must not be blank for WASTE and ADJUSTMENT`, `size must be between 3 and 255`) and `unitCost` (`can only be sent with a PURCHASE`).

The ledger is the record of what happened, so **a movement is never edited or deleted**: there is no `PUT` or `DELETE`, and a mistake is corrected with a new movement (an `ADJUSTMENT`, or a count). A database trigger rejects updates and deletes of `stock_movements`; the only change it allows is the database clearing the author's link when their account is deleted, and the movement keeps their name. Like the other append-only triggers, it doesn't stop a `TRUNCATE`: see [Known limits](#known-limits).

Movements of the same product take turns: each one is recorded while its product's row is locked, and its `stockAfter` is the stock read under that lock plus its quantity, so two movements sent at once never start from the same stock. Changing a product's unit, deactivating it and deleting it take the same lock, as does adding it to a purchase order.

The listing (`GET /api/stock-movements/establishment/{id}?from&to&productId&type`) is newest business day first, then the latest recorded; every filter is optional and `from`/`to` apply to `date`, over at most 366 days (see [Listing ranges](#listing-ranges)): without dates, the last 366 up to today. A movement entered for an earlier day shows the stock as it was when it was recorded.

### Stock counts

`POST /api/stock-counts` with what was found on the shelves:

```json
{ "establishmentId": 1, "date": null, "notes": "Recuento de fin de mes",
  "items": [ { "productId": 5, "countedQuantity": 8.5 }, { "productId": 6, "countedQuantity": 0 } ] }
```

For each product it records a `COUNT` movement with `countedQuantity - currentStock`, also when that is 0, to leave on record that the product was counted, so the stock becomes what was counted. `notes` (at most 255 characters) becomes each movement's `reason`. The answer, **201**, is the movements in the order of the items. It takes 1 to 500 items, each product once (**400** `fieldErrors.items`: `must not contain the same product twice`), every product of that establishment (**409** `Product 'Ron' belongs to another establishment`, `OTHER_ESTABLISHMENT`) and active (**409** `PRODUCT_INACTIVE`). The day works as for movements (**409** `The count date cannot be in the future`, `FUTURE_DATE`). An item whose difference doesn't fit a movement, for example 999999999 counted while the stock is -2, answers **400** `Validation failed` with that item in `fieldErrors`, every such item at once: `items[1].countedQuantity`: `would record a movement of 1000000001.000 from the current stock of -2.000; a movement can be at most 999999999.999 either way`. It is all or nothing, in one transaction that locks the products in id order: a movement sent at the same time is either counted against or waits for the count.

### Purchase orders and receiving

```json
{ "establishmentId": 1, "supplierId": 3, "expectedDate": "2026-11-24", "notes": null,
  "lines": [ { "productId": 5, "quantity": 2, "unitCost": 92.50 }, { "productId": 7, "quantity": 12, "unitCost": null } ] }
```

An order has 1 to 200 lines, each product once (**400** `fieldErrors.lines`), all of the order's establishment and active, and a supplier of the same establishment. It moves through these statuses:

| Request | From | To |
|---|---|---|
| `POST /api/purchase-orders` | | `DRAFT` |
| `PUT /api/purchase-orders/{id}` (replaces supplier, dates, notes and lines) | `DRAFT` | `DRAFT` |
| `DELETE /api/purchase-orders/{id}` | `DRAFT` | deleted |
| `PUT /api/purchase-orders/{id}/order` (`orderDate` = today; its products must still be active) | `DRAFT` | `ORDERED` |
| `PUT /api/purchase-orders/{id}/cancel` | `DRAFT`, `ORDERED` | `CANCELLED` |
| `PUT /api/purchase-orders/{id}/receive` | `ORDERED` | `RECEIVED` |

Any other move answers **409** with code `PURCHASE_ORDER_WRONG_STATUS` and a message that names the status, for example `Only a draft purchase order can be edited; this one is ORDERED` (also `... can be deleted`, `... can be ordered`, `Only a draft or ordered purchase order can be cancelled; ...` and `Only an ordered purchase order can be received; ...`).

Each line's `lineTotal` is its quantity times its `unitCost`, the quantity received once the order is received and the one ordered before; null without a cost. The order's `total` adds up the lines that have one, and is null when none has. Once received, a line that came in without a cost of its own has its product's cost at that moment as its `unitCost` (see below), so it counts in `lineTotal`, `total` and the expense.

Receiving takes every line of the order, each once:

```json
{ "receivedDate": null, "createExpense": true, "paymentMethod": "CARD",
  "lines": [ { "lineId": 21, "receivedQuantity": 2, "unitCost": 95.00 }, { "lineId": 22, "receivedQuantity": 0 } ] }
```

In one transaction it:

1. records a `PURCHASE` movement, with the order's `purchaseOrderId`, for each line with `receivedQuantity > 0`, valued at the line's cost: the one received if sent, else the one ordered, else the product's cost at that moment. That cost is kept as the line's `unitCost`, so the line's `lineTotal`, the order's `total`, the movement's `value` and the expense's `amount` all count the same euros: 5 units at 10.00 and 4 units without a cost of a product that costs 3.00 make a total, and an expense, of 62.00. A line that came in without a cost while its product has none either stays without one, and is left out of the total and the expense; a line received as 0 keeps the cost it had;
2. makes each cost received the product's `unitCost`;
3. if `createExpense` is true, creates an [expense](#expenses): category `SUPPLIES`, `date` = the day received, `supplier` = the supplier's name, `description` = `Pedido #<id>`, `amount` = the order's total, and the `paymentMethod` sent.

`receivedDate` defaults to today in the establishment's time zone and can't be later (**409** `The received date cannot be in the future`, `FUTURE_DATE`). A line can be received as 0 (it didn't come), but not all of them (**409** `Nothing was received; cancel the purchase order instead`, `PURCHASE_ORDER_NOTHING_RECEIVED`). A missing line or one of another order answers **400** (`Every line of the purchase order must be sent; line 22 is missing`, `Line 99 is not a line of this purchase order`). A line that would leave its product's stock past what it can hold answers **400** `Validation failed` with `fieldErrors` on `lines[i].receivedQuantity`, `i` being its place in the request, and nothing is received. With `createExpense` true, `paymentMethod` is required (**400** `fieldErrors.paymentMethod`), and a total that is null or 0 answers **409** `The received purchase order has no total to record as an expense; give its lines a unit cost or receive it without an expense` (`PURCHASE_ORDER_NO_TOTAL`). The expense goes through the same rules as one entered by hand: a cash expense on a day whose cash closing is closed answers that **409** (`The cash closing of 2026-11-20 is closed; ...`, `code` `CASH_DAY_CLOSED`, the day in `details.date`), and **nothing at all is received**: no movement, no change to the products' costs, and the order stays `ORDERED` with its lines as they were.

The expense's `purchaseOrderId` is the order's id. It can't be edited (**409** `EXPENSE_FROM_PURCHASE_ORDER`, see [Expenses](#expenses)), so its amount stays the order's total. Deleting it (`DELETE /api/expenses/{id}`) is allowed, with the closed-day rule of any cash expense: the order stays received with its movements and its `expenseId` becomes null, and a corrected expense can then be entered by hand.

**Lock order.** Requests take the row locks they need in one order, leaving out those they don't need, so no two requests can wait for each other: first the establishment, then a purchase order, then products in id order. Receiving takes all three, because its expense needs the establishment's lock, which cash closings and expenses also take; editing and ordering take the order and then its products, cancelling and deleting only the order; movements, counts and changes to a product only products; deleting an expense takes the establishment and then, through the foreign key that clears `expenseId`, the order; deleting an establishment takes the establishment and then all its products, and deleting a supplier the products it supplies, before they check for movements and orders.

### Inventory report and CSV exports

`GET /api/reports/establishment/{id}/inventory?from&to` (both required, at most 366 days):

```json
{
  "from": "2026-11-01", "to": "2026-11-30", "currency": "EUR",
  "stockValue": 1240.50, "productsCount": 42, "lowStockCount": 3, "negativeStockCount": 1,
  "purchasesValue": 2310.00, "wasteValue": 64.20, "countDifferenceValue": -18.40,
  "wasteByProduct": [ { "productId": 5, "productName": "Ron Barceló", "unit": "BOTTLE", "quantity": 2.000, "value": 23.00 } ],
  "lowStock": [ { "productId": 9, "productName": "Tónica", "unit": "UNIT", "currentStock": 4.000, "minStock": 12.000,
                  "supplierId": 3, "supplierName": "Schweppes" } ]
}
```

- As of now, counting only active products: `stockValue` (the sum of their `stockValue`), `productsCount`, `lowStockCount`, `negativeStockCount`, and `lowStock` by category and then name.
- For the movements dated in the range, each valued at the cost it kept: `purchasesValue`, `wasteValue` (in positive), `countDifferenceValue` (net; negative when the counts found less than the ledger said) and `wasteByProduct`, the ten largest wastes by value (a product whose waste had no cost shows `value` null, last). Movements without a cost add nothing.

The report runs the same four queries whatever the range and the data.

In the same format as the [other exports](#csv-exports) (UTF-8 with a BOM, `;`, CRLF, Spanish headers, `dd/MM/yyyy`, decimal commas, typed text such as product and supplier names, reasons and notes that can't run as a formula). Quantities are written without trailing zeros (`2,5`, `3`):

| Export | File | Columns |
|---|---|---|
| `GET /api/reports/establishment/{id}/inventory.csv` | `inventario_{today}.csv` | The stock now, one row per active product, by category and then name: `Producto;Categoría;Unidad;Proveedor;Stock actual;Stock mínimo;Bajo mínimo;Coste unitario (EUR);Valor del stock (EUR)`, with the category and unit in Spanish and `Bajo mínimo` as `Sí` or `No`. |
| `GET /api/reports/establishment/{id}/stock-movements.csv?from&to` | `movimientos-stock_{from}_{to}.csv` | One row per movement of the range, oldest first: `Fecha;Producto;Unidad;Tipo;Cantidad;Coste unitario (EUR);Valor (EUR);Stock después;Motivo;Pedido;Registrado el;Registrado por`. `Tipo` is `Compra`, `Merma`, `Consumo`, `Ajuste` or `Recuento`; `Pedido` is the purchase order's id; `Registrado el` is in the establishment's time zone. `Valor (EUR)` has the sign of `Cantidad`, negative for what went out, so adding up the column gives the report's signed values: a count that found 1.5 units fewer at 12.00 is `-1,5` and `-18,00`. This differs from the JSON `value`, which is always `\|quantity\| × unitCost`. |

### Deleting and inventory records

Stock movements and purchase orders (any status) are the establishment's inventory record:

| Request | Message of the 409 |
|---|---|
| `DELETE /api/establishments/{id}` | `Cannot delete an establishment that has stock movements or purchase orders; keep it to keep its inventory records` |
| `DELETE /api/users/{id}` of an owner | `Cannot delete an owner whose establishments have stock movements or purchase orders; keep the owner to keep those inventory records` |

The database refuses these deletes as well, and deleting a product with movements or order lines, a supplier with orders or an order with movements (`ON DELETE RESTRICT`). A movement, count or order being recorded while an establishment or supplier is deleted doesn't turn into an error of its own: the delete waits for it and answers the 409 above (or `SUPPLIER_HAS_ORDERS`), or the other request waits for the delete and then finds nothing to record against (**404**, or the generic **409** `The operation conflicts with existing data`). Without them, an establishment's products and suppliers go with it. Deleting the account that recorded a movement or created an order keeps that person's name and only clears the link.

### Inventory endpoints

All of them are for the establishment's owner or an admin; a worker gets **403**, before any validation, and so does an owner for another owner's establishment, also when one of its suppliers or products is named in a request body.

| Method and path | Body | Answer |
|---|---|---|
| `GET /api/suppliers/establishment/{id}` | | **200** suppliers by name |
| `POST /api/suppliers` | the supplier | **201** the supplier |
| `PUT /api/suppliers/{id}` | the supplier | **200** the supplier |
| `DELETE /api/suppliers/{id}` | | **204** |
| `GET /api/products/establishment/{id}?includeInactive=false` | | **200** products by category and name |
| `GET /api/products/{id}` | | **200** the product |
| `POST /api/products` | the product | **201** the product |
| `PUT /api/products/{id}` | the product | **200** the product |
| `PUT /api/products/{id}/active` | `{ "active": false }` | **200** the product |
| `DELETE /api/products/{id}` | | **204** |
| `POST /api/stock-movements` | the movement | **201** the movement |
| `GET /api/stock-movements/establishment/{id}?from&to&productId&type` | | **200** movements, newest first; at most 366 days (see [Listing ranges](#listing-ranges)) |
| `POST /api/stock-counts` | the count | **201** the `COUNT` movements |
| `GET /api/purchase-orders/establishment/{id}?status&from&to` | | **200** orders, newest first; `from` and `to` are days of creation in the establishment's time zone, at most 366 days (see [Listing ranges](#listing-ranges)) |
| `GET /api/purchase-orders/{id}` | | **200** the order |
| `POST /api/purchase-orders` | the order | **201** the order, `DRAFT` |
| `PUT /api/purchase-orders/{id}` | the order | **200** the order |
| `DELETE /api/purchase-orders/{id}` | | **204** |
| `PUT /api/purchase-orders/{id}/order` | | **200** the order, `ORDERED` |
| `PUT /api/purchase-orders/{id}/cancel` | | **200** the order, `CANCELLED` |
| `PUT /api/purchase-orders/{id}/receive` | the receipt | **200** the order, `RECEIVED` |
| `GET /api/reports/establishment/{id}/inventory?from&to` | | **200** the report |
| `GET /api/reports/establishment/{id}/inventory.csv` | | **200** `text/csv` file |
| `GET /api/reports/establishment/{id}/stock-movements.csv?from&to` | | **200** `text/csv` file |

A movement and an order look like this:

```json
{
  "id": 88, "productId": 5, "productName": "Ron Barceló", "unit": "BOTTLE", "type": "PURCHASE",
  "quantity": 6.000, "unitCost": 11.50, "value": 69.00, "reason": null, "date": "2026-11-20",
  "createdAt": "2026-11-20T10:15:00Z", "createdByName": "Lucía Pérez", "purchaseOrderId": 12, "stockAfter": 8.500
}
```

```json
{
  "id": 12, "establishmentId": 1, "supplierId": 3, "supplierName": "Mahou", "status": "RECEIVED",
  "orderDate": "2026-11-18", "expectedDate": "2026-11-20", "receivedDate": "2026-11-20", "notes": null,
  "lines": [ { "id": 21, "productId": 5, "productName": "Ron Barceló", "unit": "BOTTLE", "quantity": 6.000,
               "unitCost": 11.50, "receivedQuantity": 6.000, "lineTotal": 69.00 } ],
  "total": 69.00, "expenseId": 40, "createdAt": "2026-11-18T09:00:00Z", "createdByName": "Lucía Pérez"
}
```

## Email notifications

Workers get an email through [Resend](https://resend.com) whenever their schedule changes:

| Trigger | Email |
|---|---|
| A shift is created for them (one per shift in a batch) | New shift. A night shift shows its end as `02:00 (+1)` |
| One of their shifts is edited | Shift changed, showing before and after |
| One of their shifts is deleted, or moved to another worker | Shift cancelled |
| They are assigned to an event (one per assignment in a batch) | Assigned to event |
| An event they are assigned to is edited | Event changed, showing before and after |
| They are unassigned, the event or its zone is deleted, or their shift no longer covers the event | No longer assigned, with the reason |

Emails are sent asynchronously and only after the transaction commits, so a rejected request (for example, a batch of shifts where one fails) sends nothing. A failed delivery never fails the API request: it is logged as a warning (recipient only, never the body). Every attempt, successful or not, is recorded in the `email_logs` table.

Resend is configured with `RESEND_API_KEY` (required) and `RESEND_FROM` (optional, defaults to `Khronos <onboarding@resend.dev>`). Calls to Resend give up after `RESEND_CONNECT_TIMEOUT` (default `PT5S`) to connect and `RESEND_READ_TIMEOUT` (default `PT10S`) to answer.

## API overview

All endpoints are under `/api` and need a bearer token, except `POST /api/auth/login` and `GET /actuator/health`. The full interactive reference is in Swagger UI (see [Running locally](#running-locally)).

| Resource | Base path | Notes |
|---|---|---|
| Auth | `/api/auth` | `POST /login` returns a token valid for one hour. |
| Users | `/api/users` | `GET /me` returns the logged-in user. `POST /create?role=...` is for admins only. `POST /create/worker` creates a user with the `WORKER` role. `PUT /{id}/password` changes the password and revokes the user's older tokens. `PUT /{id}/password/reset` lets an admin or owner set a new one. `GET /establishment/{id}` lists the accounts of an establishment's workers. |
| Establishments | `/api/establishments` | You can also list them by owner: `/owner/{ownerId}`. |
| Zones | `/api/zones` | You can also list them by establishment: `/establishment/{id}`. |
| Workers | `/api/workers` | Identified by their user ID. You can list them by establishment. `POST /with-user` creates the account and links it in one step. `PUT /{id}/active` turns a worker on or off. |
| Worker shifts | `/api/worker-shifts` | `POST` takes a **list**. You can list shifts by owner, worker or zone. |
| Events | `/api/events` | `POST` takes a **list**. You can list events by zone or establishment. |
| Worker events | `/api/worker-events` | `POST` takes a **list** of worker/event pairs. You can list assignments by worker, event or establishment. |
| Time entries | `/api/time-entries` | Workers clock in and out; owners enter, clock out, correct and void entries. See below. |
| Rates | `/api/workers/{workerId}/rates` | A worker's hourly rates. Owners and admins only. |
| Reports | `/api/reports` | Hours, lateness and costs, the business summary and the inventory, as JSON or CSV. |
| Cash closings | `/api/cash-closings` | One per establishment and business day, closed and reopened with a history. Owners and admins only. |
| Expenses | `/api/expenses` | An establishment's expenses. Owners and admins only. |
| Suppliers | `/api/suppliers` | An establishment's suppliers. Owners and admins only. |
| Products | `/api/products` | Products with their stock worked out from the ledger. Owners and admins only. |
| Stock movements | `/api/stock-movements` | The stock ledger: only `POST` and listings, never edited. Owners and admins only. |
| Stock counts | `/api/stock-counts` | Physical counts, recorded as `COUNT` movements. Owners and admins only. |
| Purchase orders | `/api/purchase-orders` | Orders to suppliers, from draft to received. Owners and admins only. |
| Health | `/actuator/health` | Public. Answers `{"status":"UP"}` without details, or 503 if the database is down. |

**Date filters.** Every listing of shifts (by owner, worker or zone), events (by zone or establishment) and assignments by establishment accepts optional `from` and `to` query parameters, as `YYYY-MM-DD`, both inclusive. Events and assignments are filtered by the event's date. Without them the whole list comes back. For example `GET /api/worker-shifts/owner/3?from=2026-11-01&to=2026-11-30`. `from` after `to` answers **400**.

### Endpoints added in the previous version

| Method and path | Body | Answer | Who |
|---|---|---|---|
| `POST /api/workers/with-user` | `{ "establishmentId": 1, "user": { same fields as POST /api/users/create/worker } }` | **201** `{ "user": {...}, "worker": {...} }`. The account and the link are saved together or not at all. | The establishment's owner or an admin |
| `PUT /api/workers/{id}/active` | `{ "active": false }` | **200** the worker. Repeating it changes nothing. | The worker's owner or an admin |
| `PUT /api/users/{id}/password/reset` | `{ "newPassword": "..." }` | **204**. The user's older tokens stop working. | An admin (not on another admin) or the worker's owner |
| `GET /api/worker-events/establishment/{id}?from&to` | | **200** list of assignments. A worker gets only their own. | The owner, its workers or an admin |
| `GET /api/users/establishment/{id}` | | **200** list of users | The owner or an admin |

`DELETE /api/workers/deactivate/{id}` still works but is deprecated: it toggles the flag, so a retried request undoes itself. Use `PUT /api/workers/{id}/active`.

### Time tracking, rates and reports endpoints

"Owner" means the owner of the establishment the data belongs to. Admins can call all of these except the three that only workers use.

| Method and path | Body | Answer | Who |
|---|---|---|---|
| `POST /api/time-entries/clock-in` | `{ "note": "..." }`, optional, at most 255 characters | **201** the entry | A worker, for themselves |
| `POST /api/time-entries/clock-out` | `{ "breakMinutes": 30, "note": "..." }`, both optional; `breakMinutes` 0 to 600 | **200** the entry | A worker, for themselves |
| `GET /api/time-entries/me/open` | | **200** the open entry, or **204** if there is none | A worker |
| `POST /api/time-entries` | `{ "workerId": 5, "clockIn": "2026-11-20T17:00:00Z", "clockOut": null, "breakMinutes": 0, "reason": "..." }` | **201** the entry, with `source` `MANAGER` | The owner or an admin |
| `POST /api/time-entries/{id}/clock-out` | `{ "breakMinutes": 30, "reason": "..." }`; `breakMinutes` optional, 0 to 600; `reason` 3 to 255 characters | **200** the entry, `status` `CLOSED` | The owner or an admin |
| `PUT /api/time-entries/{id}` | `{ "clockIn": "...", "clockOut": "..." or null, "breakMinutes": 0, "reason": "..." }` | **200** the entry, `isEdited` true | The owner or an admin |
| `PUT /api/time-entries/{id}/void` | `{ "reason": "..." }` | **200** the entry, `status` `VOIDED` | The owner or an admin |
| `GET /api/time-entries/{id}/history` | | **200** the changes, oldest first | The owner, an admin or the entry's worker |
| `GET /api/time-entries/worker/{workerId}?from&to&includeVoided` | | **200** entries by clock-in | The owner, an admin or the worker |
| `GET /api/time-entries/establishment/{id}?from&to&includeVoided` | | **200** entries by clock-in | The owner or an admin |
| `GET /api/time-entries/establishment/{id}/open` | | **200** the entries open right now | The owner or an admin |
| `GET /api/workers/{workerId}/rates` | | **200** rates, the latest `effectiveFrom` first | The owner or an admin |
| `POST /api/workers/{workerId}/rates` | `{ "hourlyRate": 12.50, "effectiveFrom": "2026-11-01" }` | **201** the rate | The owner or an admin |
| `DELETE /api/workers/{workerId}/rates/{rateId}` | | **204**, only for a rate that hasn't taken effect yet | The owner or an admin |
| `GET /api/reports/establishment/{id}/hours?from&to` | | **200** the hours report | The owner or an admin |
| `GET /api/reports/worker/{workerId}/days?from&to` | | **200** the daily report; costs are null for the worker | The owner, an admin or the worker |
| `GET /api/reports/establishment/{id}/hours.csv?from&to` | | **200** `text/csv` file | The owner or an admin |
| `GET /api/reports/establishment/{id}/time-entries.csv?from&to&includeVoided` | | **200** `text/csv` file | The owner or an admin |
| `GET /api/reports/worker/{workerId}/time-entries.csv?from&to&includeVoided` | | **200** `text/csv` file | The owner, an admin or the worker |

A time entry looks like this:

```json
{
  "id": 41, "workerId": 5, "workerFirstName": "Lucía", "workerLastName": "Pérez", "establishmentId": 1,
  "businessDate": "2026-11-20", "clockIn": "2026-11-20T17:02:00Z", "clockOut": "2026-11-21T01:05:00Z",
  "breakMinutes": 20, "workedMinutes": 463, "shiftId": 12, "source": "WORKER", "status": "CLOSED",
  "isEdited": false, "isStale": false, "note": null
}
```

The `from` and `to` filters of the time entry listings apply to the business day, are optional and include both ends. Establishment responses now include `timeZone`, and shift responses include `endsNextDay` and `durationMinutes`.

### Typical flow

0. Each user signs in with `POST /api/auth/login` and sends the returned token as `Authorization: Bearer ...` on the calls below.
1. An admin creates a user with the `OWNER` role (`POST /api/users/create?role=OWNER`) and an establishment for them (`POST /api/establishments`).
2. The owner creates zones (`POST /api/zones`).
3. The owner creates each worker's account and links it to the establishment in one call (`POST /api/workers/with-user`), or in two steps with `POST /api/users/create/worker` and `POST /api/workers`.
4. The owner schedules shifts:
   ```json
   POST /api/worker-shifts
   [
     { "workerId": 5, "zoneId": 2, "date": "2026-11-14", "startTime": "18:00", "endTime": "23:00" },
     { "workerId": 6, "zoneId": 2, "date": "2026-11-14", "startTime": "17:00", "endTime": "22:00" }
   ]
   ```
5. The owner creates events:
   ```json
   POST /api/events
   [
     { "title": "Wedding dinner", "dateTime": "2026-11-14T19:00:00", "zoneId": 2, "peopleNumber": 120, "description": "Set menu" },
     { "title": "Cocktail hour", "dateTime": "2026-11-14T21:00:00", "zoneId": 2, "peopleNumber": 80 }
   ]
   ```
6. The owner assigns workers whose shifts cover those events:
   ```json
   POST /api/worker-events
   [
     { "workerId": 5, "eventId": 10 },
     { "workerId": 5, "eventId": 11 }
   ]
   ```

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

`code` only appears on the errors in the table below: the authentication errors, the cash closing and expense conflicts, and the inventory conflicts. It is a stable value for clients to tell these cases apart, while the wording of `message` may change. Clients should treat a missing or unknown `code` like any other error with that status.

```json
{
  "timestamp": "2026-11-10T10:15:30Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "This account is disabled",
  "path": "/api/auth/login",
  "code": "ACCOUNT_DISABLED"
}
```

| `code` | Status | When |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 | Login with an unknown username or a wrong password, whatever the state of the account |
| `ACCOUNT_DISABLED` | 401 | Login with the right password for a disabled account, or any request with a token of a disabled account |
| `SESSION_EXPIRED` | 401 | The token has expired, was revoked by a password change, belongs to a deleted user or is otherwise invalid |
| `AUTHENTICATION_REQUIRED` | 401 | An endpoint that needs a token was called without one |
| `TOO_MANY_ATTEMPTS` | 429 | Too many failed logins for the username and IP, or for the IP; wait the seconds given in `Retry-After` |
| `CASH_CLOSING_EXISTS` | 409 | The establishment already has a cash closing for that business day; `details.date` is the day |
| `CASH_CLOSING_CLOSED` | 409 | Editing or deleting a closed cash closing, or closing one that is already closed |
| `CASH_CLOSING_NOT_CLOSED` | 409 | Reopening a cash closing that is not closed |
| `CASH_CLOSING_WAS_CLOSED` | 409 | Deleting a draft that was closed before and reopened |
| `DIFFERENCE_REASON_REQUIRED` | 409 | Closing a day whose cash difference is over the threshold without a `differenceReason` |
| `CASH_NOT_COUNTED` | 409 | Closing a day whose `countedCash` is still `null` |
| `CASH_DAY_CLOSED` | 409 | Adding, changing, moving or deleting a cash expense of a day whose closing is closed, also receiving a purchase order with a cash expense on that day; `details.date` is that day |
| `FUTURE_DATE` | 409 | A cash closing's `businessDate`, an expense's `date`, or the `date` of a stock movement or count or the `receivedDate` of a purchase order is after today in the establishment's time zone |
| `SUPPLIER_NAME_TAKEN` | 409 | Another supplier of the establishment has that name, whatever its case |
| `SUPPLIER_HAS_ORDERS` | 409 | A supplier with purchase orders can't be deleted |
| `PRODUCT_NAME_TAKEN` | 409 | Another product of the establishment has that name, whatever its case |
| `PRODUCT_INACTIVE` | 409 | The product is inactive, so it takes no movements, counts or order lines |
| `PRODUCT_UNIT_LOCKED` | 409 | The unit of a product with movements or purchase order lines, in any status, can't change |
| `PRODUCT_HAS_HISTORY` | 409 | A product with movements or order lines can't be deleted; deactivate it |
| `OTHER_ESTABLISHMENT` | 409 | A supplier or product named in the request is of another establishment of the same owner |
| `PURCHASE_ORDER_WRONG_STATUS` | 409 | The purchase order's status doesn't allow that edit, delete or transition |
| `PURCHASE_ORDER_NOTHING_RECEIVED` | 409 | Every line of the receipt is 0; cancel the order instead |
| `PURCHASE_ORDER_NO_TOTAL` | 409 | An expense was asked for but the total received is unknown or 0 |
| `EXPENSE_FROM_PURCHASE_ORDER` | 409 | Editing an expense that receiving a purchase order created; delete it and enter it again instead. `details.purchaseOrderId` is that order |

`details` only comes with `CASH_CLOSING_EXISTS`, `CASH_DAY_CLOSED` and `EXPENSE_FROM_PURCHASE_ORDER`, and is left out otherwise. It holds what the error is about as strings, so clients don't have to parse the message: `date`, the business day as `YYYY-MM-DD`, for the first two, and `purchaseOrderId`, the order's id as a string such as `"12"`, for the third:

```json
{
  "timestamp": "2026-11-20T21:15:30Z",
  "status": 409,
  "error": "Conflict",
  "message": "The cash closing of 2026-11-20 is closed; reopen it to add, change or delete cash expenses of that day",
  "path": "/api/expenses",
  "code": "CASH_DAY_CLOSED",
  "details": { "date": "2026-11-20" }
}
```

| Status | When |
|---|---|
| 400 | Validation failed, or the JSON, a parameter or a date/time format is invalid, or a batch has too many items |
| 401 | Wrong username or password at login, or the token is missing, expired, revoked or belongs to a disabled account |
| 403 | You aren't allowed to access the resource |
| 404 | The resource or endpoint doesn't exist |
| 409 | A business rule was broken, or the data conflicts with existing records |
| 429 | Too many failed logins; wait the seconds given in `Retry-After` |

## Running locally

Copy `.env.example` to `.env` and fill it in. It lists every variable the app reads, with its default. At least set `JWT_SECRET` (`openssl rand -base64 48`), `RESEND_API_KEY` and, on a new database, `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD` and `APP_ADMIN_EMAIL` so you can sign in.

**With Docker** (no Java or PostgreSQL needed):

```bash
docker compose up --build
```

This starts PostgreSQL 16 and the API on http://localhost:8080. The API reads `.env` but always uses the compose database, whatever `DB_URL` says. `docker compose down -v` deletes the database.

**With Maven**, against your own PostgreSQL (the `DB_*` variables in `.env`): run `./mvnw spring-boot:run`, or start `KhronosDemoApplication` from your IDE.

Swagger UI is at http://localhost:8080/swagger-ui.html: call `POST /api/auth/login`, then paste the `accessToken` into **Authorize**.

To let a browser app on another origin call the API, list it in `CORS_ALLOWED_ORIGINS` (comma-separated, for example `http://localhost:5173`). It is empty by default, which allows no cross-origin calls.

### Database migrations

Flyway owns the schema. The scripts are in `src/main/resources/db/migration` and run on startup; Hibernate only checks that the entities match (`ddl-auto=validate`).

- `V1__baseline.sql` is the schema that `ddl-auto=update` used to create.
- On a database that already has those tables but no Flyway history (for example the Supabase database), the first start marks V1 as applied without running it, keeps the data, and runs only V2 onwards (`baseline-on-migrate`). `V2` adds the pieces an older database might be missing.
- `V4` to `V7` add the establishment's time zone (existing ones get `Europe/Madrid`), the generated interval columns of shifts, hourly rates, and time entries with their history. They only add columns, tables, indexes and a trigger, so they run on a database that already has data.
- `V8` changes the time entries' foreign keys to their worker and establishment, and the history's to its entry, from `ON DELETE CASCADE` to `ON DELETE RESTRICT`. Only the delete action changes, so the existing rows already meet the new constraints.
- `V9` and `V10` add expenses, and cash closings with their history and its append-only trigger. They only add sequences, tables, indexes, a function and a trigger, so they run on a database that already has data.
- `V11` lets a draft cash closing be saved before its cash is counted: `counted_cash` becomes nullable in `cash_closings`, and `counted_cash` and `difference` in their history. It only drops `NOT NULL` and adds checks that every existing row already meets (a closed day has counted cash; in the history both are null or neither is), so it runs on a database that already has data.
- `V12` to `V14` add the inventory: suppliers; products and stock movements with their append-only trigger; purchase orders with their lines, and the link from a movement to its order. They only add sequences, tables, indexes, a function, a trigger and one nullable column, so they run on a database that already has data.
- `V15` adds an index on `stock_movements (establishment_id, product_id) include (quantity)`, so the stock of an establishment's products is added up from the index alone. It only adds an index, so it runs on a database that already has data; writes to `stock_movements` wait while it is built.
- Any schema change goes in a new script, `V16__what_it_does.sql`, `V17__...`. Never edit a script that has already run anywhere.

### Tests

The integration tests start the whole app against a real, **empty** PostgreSQL database given by `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`, and empty every table before and after each test. Never point them at a database whose data you need: the app also reads `DB_URL` from `.env`, so override it as below. As a safety net the tests refuse to start, before migrating anything, unless the database name contains `test`. `JWT_SECRET` and `RESEND_API_KEY` must also be set (any values; no email is sent).

```bash
docker run -d --name khronos-test-db -p 5433:5432 -e POSTGRES_USER=khronos -e POSTGRES_PASSWORD=khronos -e POSTGRES_DB=khronos_test postgres:16-alpine
DB_URL=jdbc:postgresql://localhost:5433/khronos_test DB_USERNAME=khronos DB_PASSWORD=khronos \
JWT_SECRET=test-only-secret-0123456789-abcdefghijkl RESEND_API_KEY=test ./mvnw test
```

`./mvnw test -Dtest=TokenFlowTests` checks the token rules without a database. CI runs the same suite on every push and pull request (`.github/workflows/ci.yml`).

## Project structure

```
src/main/java/com/moga/khronosdemo
├── config/        OpenAPI, password encoder and clock beans, first admin bootstrap, time-tracking and cash settings
├── controllers/   REST endpoints and @PreAuthorize rules
├── dtos/          Request DTOs (with validation) and response DTOs
├── entities/      JPA entities and the Role enum
├── exceptions/    Domain exceptions and GlobalExceptionHandler
├── notifications/ Notification events and the listener that emails workers
├── mappers/       Conversions between entities and DTOs
├── repositories/  Spring Data JPA repositories, including the overlap and coverage queries
├── security/      SecurityConfig, AccessGuard, the user-details service, JWT issuing and checking, login limits
├── services/      Service interfaces and their implementations (business rules)
└── validation/    Custom constraints: batch size, password length in bytes, time zone ids, rules across fields; the OnCreate group

src/main/resources/db/migration   Flyway migrations
```
