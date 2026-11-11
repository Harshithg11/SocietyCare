# SocietyCare - Gated Community Group Booking Platform (Backend)

## 1. Project overview

SocietyCare is a REST backend for residents of gated communities. Residents of the **same society** join a
**booking campaign** for a home service (AC servicing, plumbing, cleaning, pest control, ...). When enough residents
have joined, the campaign **automatically becomes CONFIRMED** and everybody gets the discounted group price.

Example: AC servicing costs Rs 1,000. A provider creates a campaign with 20% discount and a minimum of 10 residents.
The campaign price is Rs 800. When the 10th resident joins, the campaign changes from `PENDING` to `CONFIRMED`
without any manual step. If the end date passes first, it becomes `EXPIRED`.

## 2. Problem statement

Residents who book home services individually pay full price and have to coordinate with providers one by one.
Providers lose time travelling to scattered single bookings. Group bookings reduce the cost for residents and give
providers predictable, batched work, but they need reliable rules (who can join, when a campaign is confirmed, what
happens on cancellation) and safe handling of many residents joining at the same time.

## 3. Objectives

* Let residents of one society join a shared campaign at a discounted price.
* Confirm / expire campaigns automatically with correct, race-free participant counts.
* Provide secure, role-based access for citizens, service providers and admins.
* Offer a clean, documented REST API (Swagger) that a frontend can use.

## 4. Key features

* JWT authentication (stateless), BCrypt password hashing, role-based authorization (`CITIZEN`, `SERVICE_PROVIDER`, `ADMIN`).
* Public registration can create only CITIZEN or SERVICE_PROVIDER. **ADMIN can never be self-registered.**
* Society, service offering, campaign and booking management with ownership checks (a provider only manages their own data).
* Automatic discounted price calculation, stored per campaign as a snapshot.
* Automatic `PENDING -> CONFIRMED` when the minimum is reached, optional maximum participants.
* Scheduled expiration of overdue campaigns (+ on-demand admin endpoint).
* Concurrency-safe joining/cancelling (pessimistic row lock + unique constraint).
* Centralized JSON error handling, Bean Validation, pagination and sorting, Swagger UI with JWT support.

## 5. Technology stack

| Technology | Version / notes |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.2 |
| Build | Maven |
| Database | PostgreSQL (tests use in-memory H2) |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security + JWT (JJWT 0.12.6), BCrypt |
| API docs | springdoc-openapi 3.1.1 (Swagger UI) |
| Validation | Jakarta Bean Validation |
| Testing | JUnit 5, Mockito, AssertJ, Spring Security Test |
| Config | `application.yml` |

## 6. Architecture

Layered architecture. Controllers handle HTTP only, services contain the business rules and transactions,
repositories talk to the database, DTOs (Java records) are the only objects exposed by the API, entities never leave the service layer.

```
Client -> Controller (validation, @PreAuthorize) -> Service (rules, @Transactional) -> Repository -> PostgreSQL
                      \-> GlobalExceptionHandler (uniform JSON errors)
JwtAuthenticationFilter (security layer) authenticates every request from the Authorization header.
```

## 7. Folder structure

```
SocietyCare-Backend
|-- pom.xml
|-- README.md
|-- .gitignore
`-- src
    |-- main
    |   |-- java/com/societycare
    |   |   |-- SocietyCareApplication.java
    |   |   |-- config        ApplicationConfig, OpenApiConfig, DataInitializer (dev seed data)
    |   |   |-- controller    Auth, User, Society, ServiceOffering, Campaign, Booking
    |   |   |-- dto/request   RegisterRequest, LoginRequest, SocietyRequest, ServiceOfferingRequest, CampaignRequest, ...
    |   |   |-- dto/response  AuthResponse, UserResponse, CampaignResponse, BookingResponse, ApiErrorResponse, ...
    |   |   |-- entity        BaseEntity, Society, User, ServiceOffering, BookingCampaign, Booking
    |   |   |-- enums         Role, CampaignStatus, BookingStatus, ServiceCategory
    |   |   |-- exception     GlobalExceptionHandler + custom exceptions
    |   |   |-- repository    5 Spring Data repositories
    |   |   |-- scheduler     CampaignExpirationScheduler
    |   |   |-- security      SecurityConfig, JwtService, JwtAuthenticationFilter, CustomUserDetailsService, ...
    |   |   |-- service       service interfaces + CurrentUserService
    |   |   |-- service/impl  service implementations (all business logic)
    |   |   `-- util          PriceCalculator, ErrorResponseWriter
    |   `-- resources/application.yml
    `-- test
        |-- java/com/societycare   unit tests (Mockito) and integration tests (Spring + H2)
        `-- resources/application-test.yml
```

## 8. Database schema and relationships

```
societies 1 ----< users                (users.society_id, nullable for ADMIN)
societies 1 ----< service_offerings    (service_offerings.society_id)
users     1 ----< service_offerings    (service_offerings.service_provider_id)  -- the owning SERVICE_PROVIDER
service_offerings 1 ----< booking_campaigns   (booking_campaigns.service_offering_id)
societies 1 ----< booking_campaigns    (booking_campaigns.society_id)
booking_campaigns 1 ----< bookings     (bookings.campaign_id)
users     1 ----< bookings             (bookings.citizen_id)
```

Important constraints: `users.email` unique; `bookings (citizen_id, campaign_id)` unique (no duplicate joins);
`societies (society_name, pincode)` unique; CHECK constraints on prices and participant numbers;
indexes on all foreign keys, `campaign.status` and `campaign.end_date`.
All relations are `LAZY`, there are no cascades (no entity owns another's lifecycle), no bidirectional lists.

## 9. Business rules (decisions you can explain in an interview)

**Joining** `POST /api/bookings/campaigns/{id}/join` (CITIZEN only): the campaign row is locked
(`SELECT ... FOR UPDATE`), then: same society -> status check -> campaign has started -> end date not passed ->
no active booking yet -> maximum not reached -> create booking -> increment count -> if `count >= minimum` the campaign
becomes `CONFIRMED`. All in one transaction.

* A campaign accepts joins while `PENDING`. A `CONFIRMED` campaign **with a maximum** keeps accepting joins until the maximum
  is reached (so the group can fill up); a `CONFIRMED` campaign without a maximum is closed. `EXPIRED`/`COMPLETED` never accept joins.
* If a join attempt finds that a `PENDING` campaign is past its end date, it is marked `EXPIRED` (and the join is rejected).
* A citizen who cancelled can join again: the same booking row is reactivated (the unique constraint stays valid).

**Cancelling** `PUT /api/bookings/{id}/cancel`: only the owner (or an ADMIN), only `BOOKED` bookings.
Citizens can cancel only while the campaign is `PENDING`. After `CONFIRMED` the group price is committed, so citizens must
ask an admin; an admin may cancel on a `CONFIRMED` campaign (it stays `CONFIRMED`). Nobody can cancel on `EXPIRED`/`COMPLETED`.

**Lifecycle:** `PENDING -> CONFIRMED` (automatic), `PENDING -> EXPIRED` (scheduler / admin endpoint / join attempt),
`CONFIRMED -> COMPLETED` (owning provider or admin). `EXPIRED` and `COMPLETED` are final.

**Concurrency:** every operation that changes participants or status loads the campaign with a pessimistic write lock, so
simultaneous requests are processed one after another. `@Version` and the unique constraint are extra safety nets.

**Expiration:** `CampaignExpirationScheduler` runs daily at 01:00 (`app.campaign.expiration-cron`) and expires every
`PENDING` campaign whose end date has passed with one UPDATE statement. Admins can run the same logic with
`POST /api/campaigns/expire-overdue`.

**Other decisions:** `GET /api/societies` and `GET /api/societies/{id}` are public and read-only so that the registration
form can show a society list. Services are scoped to one society (a campaign's society must match its service's society).
Campaigns with bookings, services used by campaigns, and societies with data cannot be deleted (HTTP 409);
deactivate a service with `active=false` instead.

## 10. Prerequisites

* JDK 21 (check with `java -version`)
* Maven 3.9+ (check with `mvn -version`) - IntelliJ also bundles Maven
* PostgreSQL 14+ (any recent version)
* IntelliJ IDEA (Community is enough) with the Lombok plugin (bundled in recent versions)

## 11. PostgreSQL installation and configuration

1. Install PostgreSQL from https://www.postgresql.org/download/ (remember the password you set for the `postgres` user).
2. Create the database. Using `psql`:
   ```sql
   CREATE DATABASE society_care_db;
   ```
   (or create it with pgAdmin: right-click Databases -> Create -> Database -> `society_care_db`).
3. Tell the app your password. The default in `application.yml` is `postgres`. Override it without editing files:
   * Windows (PowerShell): `$env:DB_PASSWORD="your_password"`
   * macOS/Linux: `export DB_PASSWORD=your_password`
   * or in IntelliJ: Run -> Edit Configurations -> Environment variables -> `DB_PASSWORD=your_password`

Other overridable settings: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `JWT_SECRET` (min 32 chars), `JWT_EXPIRATION_MS`,
`SERVER_PORT`, `SEED_ENABLED`, `SEED_PASSWORD`, `CORS_ALLOWED_ORIGINS`.
Tables are created automatically on first start (`ddl-auto: update`, development only).

## 12. IntelliJ IDEA setup

1. Extract `SocietyCare-Backend.zip`.
2. **File -> Open** -> select the `SocietyCare-Backend` folder (the one with `pom.xml`) -> *Open as Project* -> trust it.
3. Wait for Maven to download dependencies (bottom-right progress bar). If needed: Maven tool window -> **Reload All Maven Projects**.
4. **File -> Project Structure -> Project SDK** = JDK 21.
5. Lombok: Settings -> Plugins -> make sure *Lombok* is installed; Settings -> Build, Execution, Deployment -> Compiler ->
   Annotation Processors -> **Enable annotation processing**.
6. Set `DB_PASSWORD` (section 11) in the run configuration.

## 13. How to run

* IntelliJ: open `SocietyCareApplication.java` and click the green Run button.
* Terminal (from the project folder):
  ```bash
  mvn clean install        # compile + run tests
  mvn spring-boot:run      # start on http://localhost:8080
  ```
On start-up, sample data is created (see section 17). Disable it with `SEED_ENABLED=false`.

## 14. Swagger UI

Open **http://localhost:8080/swagger-ui/index.html**. Raw OpenAPI JSON: http://localhost:8080/v3/api-docs.
To call protected endpoints: `POST /api/auth/login` -> copy the `token` value -> click **Authorize** -> paste the token -> Authorize.

## 15. JWT authentication

1. `POST /api/auth/login` with email + password returns a `token` (valid for `JWT_EXPIRATION_MS`, default 1 hour).
2. Send it on every protected request: `Authorization: Bearer <token>`.
3. Missing/invalid/expired token -> `401`. Authenticated but not allowed -> `403`. Both return the JSON error format below.

The token contains only the email, role, issue and expiry time; it is signed with HMAC (`JWT_SECRET`).

## 16. API endpoints

Legend: **P** = public, **C** = CITIZEN, **S** = SERVICE_PROVIDER, **A** = ADMIN, **Any** = any logged-in user.
Collection endpoints accept `?page=0&size=20&sort=id,desc` (max size 100).

| Method | URL | Who | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | P | Register CITIZEN (default) or SERVICE_PROVIDER |
| POST | `/api/auth/login` | P | Log in, get JWT |
| GET / PUT | `/api/users/me` | Any | View / update own profile (name, phone) |
| GET | `/api/users`, `/api/users/{id}` | A | List / view users |
| POST | `/api/users` | A | Create user with any role (e.g. SERVICE_PROVIDER, ADMIN) |
| POST | `/api/societies` | A | Create society |
| GET | `/api/societies`, `/api/societies/{id}` | P | List / view societies (read-only) |
| PUT / DELETE | `/api/societies/{id}` | A | Update / delete (only if unused) |
| POST | `/api/services` | S, A | Create service offering |
| GET | `/api/services`, `/api/services/{id}` | Any | C: active services of own society; S: own; A: all |
| PUT / DELETE | `/api/services/{id}` | S (own), A | Update / delete (only if no campaign uses it) |
| POST | `/api/campaigns` | S (own service), A | Create campaign |
| GET | `/api/campaigns`, `/api/campaigns/{id}` | Any | C: own society; S: own services; A: all |
| GET | `/api/campaigns/society/{societyId}` | Any | Campaigns of a society (C: only own) |
| GET | `/api/campaigns/status/{status}` | Any | Filter by PENDING / CONFIRMED / EXPIRED / COMPLETED |
| PUT | `/api/campaigns/{id}` | S (own), A | Update a PENDING campaign |
| DELETE | `/api/campaigns/{id}` | S (own), A | Delete a campaign without bookings |
| PUT | `/api/campaigns/{id}/complete` | S (own), A | CONFIRMED -> COMPLETED |
| GET | `/api/campaigns/{id}/participants` | S (own), A | Residents with an active booking |
| POST | `/api/campaigns/expire-overdue` | A | Expire overdue PENDING campaigns now |
| POST | `/api/bookings/campaigns/{campaignId}/join` | C | Join a campaign |
| PUT | `/api/bookings/{id}/cancel` | C (own), A | Cancel a booking |
| GET | `/api/bookings/my-bookings` | C | My bookings |
| GET | `/api/bookings` | S, A | S: bookings of own services; A: all |
| GET | `/api/bookings/{id}` | owner, S (own), A | One booking |
| GET | `/api/bookings/campaign/{campaignId}` | S (own), A | Bookings of a campaign |

### Sample requests and responses

(IDs below assume a fresh database seeded by `DataInitializer`.)

**Login**
```http
POST /api/auth/login
Content-Type: application/json

{ "email": "citizen1@societycare.local", "password": "Password@123" }
```
```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "expiresAt": "2026-12-01T11:30:00Z",
  "userId": 3,
  "fullName": "Citizen One",
  "email": "citizen1@societycare.local",
  "role": "CITIZEN"
}
```

**Register a resident** (list societies first with `GET /api/societies` to get the id)
```http
POST /api/auth/register
Content-Type: application/json

{
  "fullName": "Priya Sharma",
  "email": "priya@example.com",
  "password": "Priya@1234",
  "phoneNumber": "9876543210",
  "societyId": 1
}
```
```json
{ "id": 6, "fullName": "Priya Sharma", "email": "priya@example.com", "phoneNumber": "9876543210",
  "role": "CITIZEN", "societyId": 1, "societyName": "Green Valley Residency", "createdAt": "2026-12-01T10:30:00" }
```

**Create a campaign** (as provider or admin)
```http
POST /api/campaigns
Authorization: Bearer <provider-or-admin-token>
Content-Type: application/json

{
  "campaignTitle": "AC Service Group Offer - December",
  "description": "Join your neighbours and save 20%",
  "serviceOfferingId": 1,
  "societyId": 1,
  "minimumParticipants": 10,
  "maximumParticipants": 25,
  "regularPrice": 1000.00,
  "discountPercentage": 20.00,
  "startDate": "2026-12-01",
  "endDate": "2026-12-15"
}
```
Response `201` contains `"discountedPrice": 800.00`, `"currentParticipants": 0`, `"status": "PENDING"`.

**Join a campaign** (citizen token) - `POST /api/bookings/campaigns/2/join` (the seeded campaign needs 2 residents)
```json
{
  "bookingId": 1, "campaignId": 2, "campaignTitle": "Plumbing Check-up Group Offer",
  "serviceName": "Plumbing Check-up", "societyName": "Green Valley Residency",
  "citizenId": 3, "citizenName": "Citizen One", "bookingStatus": "BOOKED",
  "discountedPrice": 425.00, "currentParticipants": 1, "minimumParticipants": 2,
  "campaignStatus": "PENDING", "bookedAt": "2026-12-01T10:35:12",
  "message": "You have successfully joined the campaign."
}
```
When `citizen2` joins, the response has `"currentParticipants": 2`, `"campaignStatus": "CONFIRMED"` and the message
*"Congratulations! The campaign has reached its minimum participant requirement and is now confirmed."*

**Cancel a booking** - `PUT /api/bookings/1/cancel` returns the booking with `"bookingStatus": "CANCELLED"`,
the decreased `currentParticipants` and `"message": "Your booking has been cancelled."`

**Error format** (all errors)
```json
{
  "timestamp": "2026-12-01T10:36:00",
  "status": 409,
  "error": "Conflict",
  "message": "You have already joined this campaign.",
  "path": "/api/bookings/campaigns/2/join"
}
```
Validation errors return `400` plus a `validationErrors` object (`{ "email": "Email must be valid" }`).
Typical codes: 400 validation / business rule, 401 not logged in, 403 not allowed (e.g. other society, other user's booking),
404 not found, 409 duplicate / campaign closed / in use.

## 17. Sample login credentials (development seed data only)

Password for every demo user: **`Password@123`** (change it with `SEED_PASSWORD`).

| Role | Email | Society |
|---|---|---|
| ADMIN | `admin@societycare.local` | - |
| SERVICE_PROVIDER | `provider@societycare.local` | Green Valley Residency |
| CITIZEN | `citizen1@societycare.local` | Green Valley Residency |
| CITIZEN | `citizen2@societycare.local` | Green Valley Residency |
| CITIZEN | `citizen3@societycare.local` | Lakeview Heights (use to see cross-society rejection) |

Seeded: 2 societies, 3 services (AC Servicing, Plumbing Check-up, Pest Control), campaign 1 (AC, needs 10, max 25, 20% off)
and campaign 2 (Plumbing, needs 2, 15% off - join with citizen1 and citizen2 to see automatic confirmation).
The seed never overwrites data (it is skipped when the admin already exists) and is disabled with `SEED_ENABLED=false`.
**Never run production with seed enabled.**

## 18. Running the tests

```bash
mvn test
```
* `PriceCalculatorTest`, `JwtServiceTest` - pure unit tests.
* `AuthServiceTest`, `CampaignServiceTest`, `BookingServiceTest` - Mockito unit tests: registration, duplicate email, BCrypt hashing,
  login, invalid login, campaign creation and validation, joining, participant count, automatic confirmation, duplicates, other society,
  maximum limit, expired campaigns, cancellation rules, invalid state transitions.
* `BookingConcurrencyTest` - Spring context + H2: 20 residents join at the same moment (max 15) and 8 attempts by the same resident.
* `SecurityAuthorizationTest` - full-stack MockMvc tests: 401 without token, role restrictions, cross-user booking access, admin registration blocked.

Integration tests use an **in-memory H2** database (`application-test.yml`), so no PostgreSQL is needed to run the tests.
H2 is not PostgreSQL; the locking behaviour is the same in principle, but verify the concurrent join manually against PostgreSQL too
(e.g. send many parallel join requests with a tool like `hey`/JMeter using different citizen tokens).

## 19. Common problems

| Problem | Fix |
|---|---|
| `password authentication failed for user "postgres"` | Set `DB_PASSWORD` (section 11). |
| `database "society_care_db" does not exist` | Run `CREATE DATABASE society_care_db;`. |
| `cannot find symbol ... getXxx()` in IntelliJ | Enable Lombok plugin + annotation processing (section 12), reload Maven. |
| `app.jwt.secret must be at least 32 characters` | Use a longer `JWT_SECRET`. |
| Port 8080 in use | `SERVER_PORT=8081`. |
| Maven cannot resolve a dependency | Check internet/proxy, then *Reload All Maven Projects*. |

## 20. Uploading to GitHub

```bash
cd SocietyCare-Backend
git init
git add .
git commit -m "Initial commit: SocietyCare backend"
git branch -M main
# create an EMPTY repository on github.com first (no README), then:
git remote add origin https://github.com/<your-username>/SocietyCare-Backend.git
git push -u origin main
```
`.gitignore` already excludes `target/`, IDE files and local secret files. Never commit real passwords or a real `JWT_SECRET`
(the values in `application.yml` are local development defaults only).

## 21. Known limitations

* The authors of this README could not run Maven in the environment where the project was generated; see the delivery notes.
  Run `mvn clean install` first and fix anything it reports.
* Password reset, e-mail verification and payments are out of scope. `ddl-auto: update` is for development; use migrations (Flyway) for production.
* No refresh tokens: when the token expires the user logs in again.
