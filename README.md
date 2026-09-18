# BadrLink - Auth Service

**The authentication and credential service powering BadrLink, built with Java, Spring Boot, PostgreSQL, Redis, and JWT.**

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=flat-square\&logo=springboot\&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square\&logo=openjdk\&logoColor=white)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat-square\&logo=postgresql\&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square\&logo=redis\&logoColor=white)](https://redis.io/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-CC0200?style=flat-square\&logo=flyway\&logoColor=white)](https://documentation.red-gate.com/flyway)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square\&logo=docker\&logoColor=white)](https://www.docker.com/)
[![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=flat-square\&logo=apachemaven\&logoColor=white)](https://maven.apache.org/)

A dedicated microservice responsible for user authentication, credential management, JWT sessions, password recovery, and account security within BadrLink.

---

## Responsibilities

This service owns:

* User registration and credentials
* Login and account lockout
* Access and refresh tokens
* Token rotation and revocation
* Logout and JWT denylisting
* Password reset and recovery
* Authentication roles (`USER` / `ADMIN`)

It **does not store** public profiles, blocks, connections, or other user profile data.

---

## Architecture

The Auth Service is responsible for authentication within BadrLink and communicates with the User Service during registration and role synchronization.

```text
                    ┌─────────────────────┐
                    │    User Service     │
                    │      :8082          │
                    └──────────┬──────────┘
                               │
                         Internal API
                               │
                               ▼
                    ┌─────────────────────┐
                    │    Auth Service     │
                    │      :8081          │
                    ├─────────────────────┤
                    │ Credentials         │
                    │ Sessions            │
                    │ Password Reset      │
                    └───────┬───────┬─────┘
                            │       │
                            ▼       ▼
                    ┌──────────┐ ┌──────────┐
                    │PostgreSQL│ │  Redis   │
                    │ auth_db  │ │  :6379   │
                    │  :5432   │ │          │
                    └──────────┘ └──────────┘
```

The service owns its own database and does not share entities or credentials with other services.

---

## API

### Authentication

| Method | Endpoint                | Description                       |
| ------ | ----------------------- | --------------------------------- |
| `POST` | `/auth/register`        | Register a new account            |
| `POST` | `/auth/login`           | Authenticate and create a session |
| `POST` | `/auth/refresh`         | Refresh access and refresh tokens |
| `POST` | `/auth/logout`          | Revoke the current session        |
| `POST` | `/auth/forgot-password` | Request a password reset          |
| `POST` | `/auth/reset-password`  | Reset the password                |

### Internal

Internal endpoints are protected with an `X-Internal-Token` and are intended for service-to-service communication.

```text
PATCH /internal/credentials/{id}/role
```

Used by the User Service to synchronize a user's role.

---

## Authentication

The service uses JWT-based authentication with short-lived access tokens and rotating refresh tokens.

```text
Login
  │
  ├── Access Token ──── 15 min
  │
  └── Refresh Token ─── 7 days
                          │
                          ▼
                    Token Rotation
                          │
                          ▼
                     Revocation
```

Access tokens contain the user's UUID, role, and a unique token identifier (`jti`).

Refresh and password reset tokens are stored only as SHA-256 hashes.

---

## Account Security

The service includes several protections around authentication and password recovery:

* BCrypt password hashing
* Account lockout after 5 failed login attempts
* 5-minute lockout window
* Refresh token rotation and replay detection
* Redis-based access-token denylist
* Single-use password reset tokens
* Password reset rate limiting
* SMTP failure circuit breaker
* No email enumeration during password recovery

---

## Data Model

The service currently manages three main entities:

```text
Credential
 ├── Email
 ├── Username
 ├── Password hash
 └── Role

RefreshToken
 ├── Token hash
 ├── Expiration
 └── Revocation state

PasswordResetToken
 ├── Token hash
 ├── Expiration
 └── Usage state
```

Authentication credentials are intentionally kept separate from the public profile data managed by the User Service.

---

## Getting Started

### Requirements

* Java 21
* Docker
* Maven (or the included Maven Wrapper)

### Environment Configuration

Copy the example environment file and configure the required variables:

```bash
cp .env.example .env
```

Then update `.env` with your local configuration if needed.

> **Note:** `.env` contains environment-specific values and should not be committed. Use `.env.example` as the template for required variables.

### Start the infrastructure

```bash
docker compose up -d
```

This starts:

* PostgreSQL on `5432`
* Redis on `6379`

### Run the service

```bash
./mvnw spring-boot:run
```

The service will be available at:

```text
http://localhost:8081
```

### Run tests

```bash
./mvnw test
```

Integration tests use **Testcontainers**, so Docker must be running.

---

## Project Structure

```text
src/
├── main/
│   ├── java/
│   │   └── com/ziyadsamhaoui/messagingauthservice/
│   │       ├── auth/
│   │       ├── credential/
│   │       ├── token/
│   │       ├── password/
│   │       └── security/
│   └── resources/
│       ├── db/migration/
│       └── application.yml
└── test/
    └── java/
```
