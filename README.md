# Smart Food Rescue and Donation Management System

A Java 17 / Spring Boot REST application with a responsive HTML, CSS, JavaScript, and Bootstrap frontend. It connects food donors with NGO/receiver accounts and supports approval and volunteer delivery tracking.

## What is implemented in this MVP

- **Authentication:** donor, NGO/receiver, and volunteer registration and login, BCrypt password hashing, signed JWT access tokens, and role-based API access.
- **Donation posting:** name, category, description, quantity in kilograms, preparation time, collection deadline, pickup address, image URL, storage, allergens, handling details, and a required safety-checklist acknowledgement.
- **Donation discovery:** authenticated receivers can browse available food and filter by category, keyword, or pickup address.
- **Request workflow:** an NGO requests a donation; the donor approves or rejects it. A database row lock reserves each available donation so two receivers cannot claim it at once.
- **Pickup/delivery tracking:** donors assign a registered volunteer. Only that volunteer can mark the donation collected and then delivered; invalid status transitions are rejected.
- **Expiry:** elapsed collection deadlines hide available listings, and a scheduled task marks available donations expired. A deadline is not a food-safety assessment.
- **Database:** MySQL is the default database. Flyway applies the initial schema in `backend/src/main/resources/db/migration`.
- **Frontend:** the existing Goodroots interface is served by Spring Boot and now reads/writes through the REST API. It does not show fabricated sample listings or activity.

The AI food-guide screen remains a rule-based preview, not an external AI integration. QR verification, notifications, image uploads, analytics/reporting, NGO verification, admin workflows, profile editing, and automated food-safety verification are not implemented in this MVP.

## Project structure

```text
Food-Rescue-System/
├── index.html
├── styles.css
├── app.js
├── README.md
└── backend/
    ├── pom.xml
    └── src/
        ├── main/java/org/goodroots/foodrescue/
        │   ├── auth/          # Registration, login, and auth DTOs
        │   ├── common/        # API errors and validation responses
        │   ├── donation/      # Donation entity, repository, service, and API
        │   ├── request/       # Request entity, workflow, and API
        │   ├── security/      # JWT authentication and Spring Security
        │   └── user/          # Users, roles, repository, and volunteer lookup
        ├── main/resources/
        │   ├── application*.properties
        │   └── db/migration/  # MySQL Flyway migrations
        └── test/java/         # H2-backed workflow integration tests
```

## Requirements

- Java 17+
- Maven 3.9+
- MySQL 8.0.16+ for the MySQL profile

The `dev` profile uses a local H2 file database so the UI can be tried without installing MySQL. H2 is for local development only; the default profile and Flyway migration target MySQL. The default profile requires database credentials and a `JWT_SECRET`; the development/test profiles use a local-only signing key.

## Run locally with MySQL

1. Create a database and a dedicated application user in MySQL:

   ```sql
   CREATE DATABASE food_rescue CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'food_rescue_app'@'localhost' IDENTIFIED BY 'change-this-password';
   GRANT ALL PRIVILEGES ON food_rescue.* TO 'food_rescue_app'@'localhost';
   ```

2. From the project root, set credentials in PowerShell and start Spring Boot:

   ```powershell
   $env:DB_USERNAME = "food_rescue_app"
   $env:DB_PASSWORD = "change-this-password"
   $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
   $secretBytes = New-Object byte[] 32
   $rng.GetBytes($secretBytes)
   $env:JWT_SECRET = [Convert]::ToBase64String($secretBytes)
   $rng.Dispose()
   .\backend\mvnw.cmd -f .\backend\pom.xml spring-boot:run
   ```

   Flyway applies the schema on the first startup. The database user needs permission to create/alter tables in `food_rescue`.

3. Open `http://localhost:8080`. Spring Boot serves the frontend and API from the same origin.

## Run locally without MySQL

From the project root:

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml spring-boot:run '-Dspring-boot.run.profiles=dev'
```

Then open `http://localhost:8080`. The development database is stored under `backend/data/food_rescue`; it persists across restarts. Do not use the development JWT secret or H2 profile for a deployed environment.

## Test

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml test
```

Tests use an isolated in-memory H2 database and cover authentication, role protection, donation creation, competing receiver requests, donor approval, volunteer assignment, and the collected-to-delivered workflow.

## MVP workflow

1. Create a **Food donor** account and post a donation.
2. Create an **NGO / food receiver** account, search listings, and request an available donation.
3. Sign back in as the donor, open **My activity**, and approve or reject the request.
4. After approval, the donor selects a registered volunteer and assigns pickup.
5. Sign in as that **Volunteer**, open **My activity**, and mark the pickup collected, then delivered.

Registration is available for DONOR, NGO, and VOLUNTEER. Public registration cannot create ADMIN accounts.

## REST API

All protected routes require `Authorization: Bearer <token>`. JSON validation errors return HTTP 400; role/ownership failures return 403; conflicting workflow state returns 409.

| Method | Route | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create a donor, NGO, or volunteer account |
| POST | `/api/auth/login` | Public | Sign in and receive a JWT |
| GET | `/api/donations?category=produce&location=Brooklyn` | Signed-in users | Browse available donations |
| GET | `/api/donations/{id}` | Donor, associated NGO receiver, assigned volunteer, admin | View details; unrelated accounts receive 404 |
| GET | `/api/donations/mine` | Donor | View own donation listings |
| POST | `/api/donations` | Donor | Create a donation |
| POST | `/api/donations/{id}/requests` | NGO | Request an available donation |
| GET | `/api/requests/mine` | Donor, NGO, volunteer | View related requests/assignments |
| PATCH | `/api/requests/{id}/decision` | Owning donor | Accept or reject a request |
| GET | `/api/users/volunteers` | Donor | Find volunteers available for assignment |
| PATCH | `/api/requests/{id}/volunteer` | Owning donor | Assign a volunteer to an accepted request |
| PATCH | `/api/requests/{id}/delivery-status` | Assigned volunteer | Advance `PICKUP_ASSIGNED → COLLECTED → DELIVERED` |

Example registration:

```json
{
  "fullName": "Community Kitchen",
  "organizationName": "Northside Community Kitchen",
  "email": "kitchen@example.org",
  "password": "replace-with-a-long-password",
  "role": "NGO"
}
```

Example donation creation (times use ISO local date-time, for example `2026-11-01T18:30:00`):

```json
{
  "foodName": "Seasonal vegetable trays",
  "category": "produce",
  "description": "Covered trays, ready for pickup",
  "quantityKg": 12.5,
  "preparationTime": "2026-11-01T12:30:00",
  "safeConsumptionDeadline": "2026-11-01T20:30:00",
  "pickupAddress": "10 Community Street, Brooklyn",
  "imageUrl": null,
  "storageConditions": "Refrigerated at 4 C",
  "allergens": "Not known",
  "handlingInformation": "Kept covered and chilled",
  "foodSafetyChecklistConfirmed": true
}
```

Set a unique, random `JWT_SECRET` of at least 32 bytes in production, use HTTPS, and configure `CORS_ALLOWED_ORIGINS` to only the deployed frontend origin.
