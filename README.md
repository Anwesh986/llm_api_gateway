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