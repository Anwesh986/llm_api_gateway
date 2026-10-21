# 🏛️ LLM API Gateway

Production-grade multi-module microservices API Gateway and intelligent router for LLM provider orchestration, built with Java 21, Spring Boot 3.3.x, Spring WebFlux, gRPC (Protobuf 3), PostgreSQL 16, and Redis 7.

---
Production-grade multi-module microservices API Gateway and intelligent router for LLM provider orchestration, built with Java 21, Spring Boot 3.3.x, Spring WebFlux, gRPC (Protobuf 3), PostgreSQL 16, and Redis 7.

---

## 📌 Project Status: Milestone 0 (Task 0.1 Complete)

Engineer 1 has scaffolded the monorepo root build configuration, directory layout, and subproject build scripts.

### 1. Monorepo & Submodule Layout
The project is configured as a multi-module Gradle build with strict dependency isolation across four submodules:

* **`gateway-common`**: Shared Java library containing Protobuf contracts (`model_router.proto`), gRPC generated stubs, shared DTOs (`ProblemDetailDTO`), and cryptographic utilities (`HmacSigner`).
* **`model-router-service`**: Standalone internal gRPC service running on port `9090` managing model routing logic and provider health registries.
* **`api-gateway`**: Public reactive edge ingress proxy running on port `8080` (Spring WebFlux, Spring Security 6, JWT, Redis rate limiting, R2DBC, SSE, and Spring for GraphQL).
* **`mock-webhook-receiver`**: Standalone consumer application used to verify HMAC-SHA256 signed outbound billing webhooks.

### 2. Environment & Tooling Baseline
* **Java:** Eclipse Adoptium JDK 21 (enforced via Gradle Java Toolchain)
* **Build Tool:** Gradle 8.10.2 via Gradle Wrapper (`gradlew`, `gradlew.bat`)
* **Build Script DSL:** Groovy DSL (`build.gradle`, `settings.gradle`)
* **Plugin Management:** Centrally defined in root `build.gradle` (`apply false` for Spring Boot `3.3.4`, Protobuf `0.9.4`, and Spring Dependency Management `1.1.6`)

### 3. Build Verification
To verify the multi-module resolution and compilation locally:

```bash
# Verify submodule discovery
./gradlew projects

# Compile all modules without running unit tests
./gradlew build -x test

## 🛠️ Environment Prerequisites & Initial Setup

To reproduce the developer workstation setup and initialize the project:

### 1. JDK 21 Installation
* Install **Eclipse Adoptium Temurin JDK 21 (LTS)**.
* Set the `JAVA_HOME` system environment variable:
  ```text
  JAVA_HOME = C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot
  Add %JAVA_HOME%\bin to your system PATH.

Verify installation:

DOS
java -version

2. Standalone Gradle 8.10.2 Installation
Download the Gradle 8.10.2 binary-only ZIP distribution from the official Gradle release portal.

Unzip to your local tools directory (e.g., C:\Gradle\gradle-8.10.2).

Add the bin directory (C:\Gradle\gradle-8.10.2\bin) to your user or system PATH variable.

Verify Gradle CLI availability:

DOS
gradle -v
Ensures Gradle 8.10.2 is running on JVM 21.

3. Repository Initialization (gradle init)
The repository root skeleton was bootstrapped using Gradle's built-in initialization wizard:
mkdir llm-api-gateway
cd llm-api-gateway
gradle init

Initialised the build.gradle for each submodule and then it was failing the bootjar failure hence tried with the command 
gradlew build -x test -x bootJar
and the build was successful

after creating the application files start with the build using the command: gradllew build -x test
# LLM API Gateway — Local Setup

## 1. Prerequisites

- Windows 10/11 with hardware virtualization enabled.
- WSL2 with Ubuntu.
- Docker Desktop with the WSL2 backend.

## 2. WSL2 Setup

Open PowerShell as Administrator:

```powershell
wsl --install
```

If WSL is already installed, update it:

```powershell
wsl --update
```

Enable the required Windows features if needed:

```powershell
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart

dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
```

Restart Windows, then verify:

```powershell
wsl --status
wsl --list --verbose
```

Ensure Ubuntu uses WSL version 2. If necessary:

```powershell
wsl --set-version Ubuntu 2
```

## 3. Docker Desktop Setup

1. Install Docker Desktop: https://www.docker.com/products/docker-desktop/
2. Enable **Use the WSL 2 based engine** under Settings → General.
3. Enable Ubuntu integration under Settings → Resources → WSL Integration.
4. Start Docker Desktop and verify:

```powershell
docker --version
docker compose version
docker info
```

## 4. Docker Compose Configuration

The `docker-compose.yml` file defines two services:

- **PostgreSQL 16:** Relational database for persistent application data.
- **Redis 7:** In-memory store for rate limiting, usage counters, and idempotency operations.

Named volumes (`postgres_data` and `redis_data`) preserve data across container restarts and recreation.

Create a `.env` file in the project root:

```dotenv
POSTGRES_DB=gateway_db
POSTGRES_USER=gateway_user
POSTGRES_PASSWORD=replace_with_a_local_password
```

Keep `.env` out of Git. Commit `.env.example` instead.

## 5. Start and Verify Services

Run these commands from the project root:

```bash
# Validate Compose configuration
docker compose config

# Start PostgreSQL and Redis
docker compose up -d

# Check service status
docker compose ps

# Verify PostgreSQL
docker compose exec postgres pg_isready -U gateway_user -d gateway_db

# Verify Redis
docker compose exec redis redis-cli ping
```

Redis should return `PONG`. Both services should eventually report `healthy`.

## 6. Useful Commands

```bash
docker compose logs -f       # Follow logs
docker compose stop          # Stop services; retain containers and data
docker compose start         # Restart stopped services
docker compose down          # Remove containers and network
docker compose ps            # Check service status
```

**Note:** Avoid `docker compose down -v` unless you intend to delete the persistent volumes and their data.

## 7. Key Concepts

- **Image vs. Container:** An image is a template; a container is an instance of that image.
- **Dockerfile vs. Compose:** A Dockerfile builds an application image; Compose orchestrates multiple services.
- **Port mapping:** `5432:5432` exposes PostgreSQL to the host on port 5432.
- **Volumes:** Persist data independently of a container's lifecycle.
- **Health checks:** Verify service readiness, not just whether a container is running.
- **Networking:** Applications running in the same Compose network connect using `postgres:5432` and `redis:6379`. Host applications use `localhost` and the published ports.

## 8. Git Guidelines

Add these entries to `.gitignore`:

```gitignore
.env
.env.*
!.env.example
data/
redis_data/
postgres_data/
build/
.gradle/
```

Commit configuration files, initialization scripts, and documentation. Do not commit credentials, database files, Redis data, or other generated runtime data.

**Security:** This configuration is for local development. Redis has no authentication configured, so do not expose it to untrusted networks or use this setup as-is in production.

## 9. Troubleshooting

- **Docker daemon unavailable:** Ensure Docker Desktop is running and WSL2 integration is enabled.
- **Port already in use:** Check ports with `netstat -ano | findstr :5432` or `netstat -ano | findstr :6379`.
- **Service unhealthy:** Inspect logs using `docker compose logs postgres` or `docker compose logs redis`.
- **Configuration errors:** Run `docker compose config` to validate the YAML.

---

**Next step:** Containerize the Java application and connect it to PostgreSQL and Redis through Docker Compose.