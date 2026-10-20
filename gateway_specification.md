# 🏛️ LLM API Gateway — Complete Engineering Specification & Implementation Manual

> **Document Type:** Production Architecture (HLD/LLD), Repository Layout, Collaborative Division & Delivery Guide  
> **Project Level:** Level 4 — API Design, Protocols & Distributed Communication  
> **Target Audience:** Engineer 1 & Engineer 2 (Collaborative Pair Programming Team)  
> **Target Stack:** Java 21, Spring Boot 3.3.x, Spring WebFlux, Spring Security 6, Spring for GraphQL, gRPC Java (Protobuf 3), PostgreSQL 16, Redis 7, Docker Compose

---

## 📑 Table of Contents

1. [Executive Summary & Problem Statement](#1-executive-summary--problem-statement)
2. [High-Level Architecture (HLD)](#2-high-level-architecture-hld)
3. [Component Architecture & Protocol Boundaries](#3-component-architecture--protocol-boundaries)
4. [Monorepo & Multi-Module Project Structure](#4-monorepo--multi-module-project-structure)
5. [Git Workflow, Branching & Commit Standards](#5-git-workflow-branching--commit-standards)
6. [Low-Level Design (LLD) & Technical Specifications](#6-low-level-design-lld--technical-specifications)
   * [6.1 Protocol Buffers & gRPC Schema](#61-protocol-buffers--grpc-schema)
   * [6.2 Relational Database Schema (PostgreSQL)](#62-relational-database-schema-postgresql)
   * [6.3 Redis Key Layout & Atomic Lua Rate Limiter](#63-redis-key-layout--atomic-lua-rate-limiter)
   * [6.4 Stateless JWT Security Pipeline](#64-stateless-jwt-security-pipeline)
   * [6.5 REST API, Error Envelopes & Versioning Engine](#65-rest-api-error-envelopes--versioning-engine)
   * [6.6 Reactive WebFlux SSE Streaming & Resilient Fallback](#66-reactive-webflux-sse-streaming--resilient-fallback)
   * 6.7 GraphQL SDL Schema & DataLoader N+1 Fix
   * 6.8 Cryptographic Webhook Engine with Idempotency
7. Granular Collaborative Task Matrix (Micro-Sprints)
8. End-to-End Verification, Curl Commands & Testing
9. Interview Readiness & Defense Guide

---

## 1. Executive Summary & Problem Statement

### 1.1 The Industry Problem

Organizations building AI-powered applications face severe operational vulnerabilities when integrating directly with commercial LLM providers (OpenAI, Anthropic, Google Gemini):

1. **Vendor Outages & Strict Rate Limits:** Provider 429 (Too Many Requests) or 503 (Service Unavailable) outages completely halt consumer applications.
2. **Protocol & Payload Fragmentation:** Each provider requires proprietary SDKs, unique JSON payloads, and divergent streaming protocols.
3. **Runaway Financial Exposure:** Unmetered client requests result in unexpected API bills without real-time per-client token throttling or budget alert triggers.
4. **Network Inefficiencies:** Web dashboards over-fetch gigabytes of chat logs just to display billing metrics, while internal microservice routing suffers from slow JSON over HTTP/1.1 serialization.

### 1.2 The Gateway Solution

The **LLM API Gateway** acts as a unified reverse proxy and intelligent router sitting between client applications and downstream AI providers. It exposes:

* **Stateless REST & Non-blocking SSE (Server-Sent Events)** for universal client ingestion.
* **Sub-millisecond gRPC / Protobuf communication** for internal model routing and health checks.
* **GraphQL with DataLoaders** for client analytics dashboards with zero over-fetching.
* **Atomic Redis Token Bucket Rate Limiting** to protect upstream provider quotas.
* **HMAC-SHA256 Signed Outbound Webhooks** with exponential backoff retries for billing alerts.

---

## 2. High-Level Architecture (HLD)

### 2.1 System Topology Diagram

```text
====================================================================================================
                                EXTERNAL CLIENT LAYER
Mobile Apps, React/Next.js Single Page Applications, External Backend Services, CLI Tools
====================================================================================================
        |                            |                            |
        | HTTP/1.1 or HTTP/2         | HTTP/1.1 (Accept: text/event-| HTTP POST /graphql
        | JSON: POST /api/v{1,2}/chat| stream): GET /api/v1/chat/stream|
        v                            v                            v
+--------------------------------------------------------------------------------------------------+
|                               EDGE SERVICE: API GATEWAY (:8080)                                  |
|                                                                                                  |
|  +--------------------------------------------------------------------------------------------+  |
|  | 1. Security Filter Chain: JWT Authentication, Signature Check (HS256), SecurityContext     |  |
|  +--------------------------------------------------------------------------------------------+  |
|                                                |                                                 |
|                                                v                                                 |
|  +--------------------------------------------------------------------------------------------+  |
|  | 2. Rate Limiting Filter: Redis Atomic Lua Token Bucket Check (Tier-based: Free/Pro)        |  |
|  +--------------------------------------------------------------------------------------------+  |
|                                                |                                                 |
|                                                v                                                 |
|  +--------------------------------------------------------------------------------------------+  |
|  | 3. API Versioning & Deprecation Engine: URI Path (/v1/) vs Header (X-API-VERSION: 2)       |  |
|  +--------------------------------------------------------------------------------------------+  |
|                        |                                                |                        |
|                        v                                                v                        |
|  +------------------------------------------+    +--------------------------------------------+  |
|  | ChatController (REST & SSE)              |    | DashboardGraphQLController                 |  |
|  | - Reactive Flux Stream                   |    | - SDL Queries & Mutations                  |  |
|  | - Heartbeat Ping Generator               |    | - DataLoader Batch Aggregators             |  |
|  +------------------------------------------+    +--------------------------------------------+  |
+--------------------------------------------------------------------------------------------------+
            |                                                     |
            | gRPC over HTTP/2 (Binary Protobuf)                  | JPA / JDBC Pool
            v (:9090)                                             v
+-------------------------------------------+      +-----------------------------------------------+
|     SERVICE 2: MODEL SELECTOR SERVICE     |      |             POSTGRESQL 16 (:5432)             |
|                                           |      |                                               |
| - ModelRouterServiceImpl (gRPC Server)    |      | - users & api_keys                            |
| - Provider Health State Registry          |      | - token_usage_records                         |
| - Dynamic Routing Matrix (Cost vs Latency)|      | - webhook_subscriptions & audit_logs          |
| - Deadlines & Channel Keep-Alive          |      +-----------------------------------------------+
+-------------------------------------------+                             ^
            | Returns RouteDecision                                       |
            v                                                             |
+-----------------------------------------------------------+             |
|       LLM PROVIDER DISPATCHER & RESILIENT FALLBACK        |             |
|                                                           |             |
| Primary: OpenAI GPT-4o --(onErrorResume: 429/5xx)--> Anthropic          |
|                                                Claude 3.5 |             |
+-----------------------------------------------------------+             |
            | Asynchronously Records Tokens & Spend                       |
            v                                                             |
+-------------------------------------------+      +-----------------------------------------------+
|              REDIS 7 (:6379)              |      |           WEBHOOK DISPATCHER WORKER           |
|                                           |      |                                               |
| - Atomic Token Bucket Counters            |      | - Scheduled / Async Thread Pool               |
| - Real-time Monthly Usage Counters        |<-----| Threshold- Exceeds 80% / 100% Budget Notif.   |
| - Distributed Idempotency Locks           | Check| - HMAC-SHA256 Payload Signature               |
+-------------------------------------------+      | - Exponential Backoff Retries + Jitter        |
                                                   +-----------------------------------------------+
                                                                          | HTTP POST
                                                                          v
                                                              [ Customer Webhook Listener ]
```

---

## 3. Component Architecture & Protocol Boundaries

### 3.1 Edge Layer vs Internal Protocol Split

| Boundary | Protocols Used | Rationale |
| :--- | :--- | :--- |
| **Client → Gateway (Ingestion)** | REST (HTTP/1.1, HTTP/2), JSON | Standardized client compatibility. Zero third-party runtime dependencies for consumer apps. |
| **Client → Gateway (Streaming)** | Server-Sent Events (SSE), UTF-8 text streams | Long-lived unidirectional stream supported natively by browser `EventSource` and curl without WebSocket upgrade overhead. |
| **Client → Gateway (Analytics)** | GraphQL over HTTP POST | Allows developer consoles to query complex hierarchical billing and metric trees without N+1 network requests or over-fetching. |
| **Gateway → Model Selector (Internal)** | gRPC over HTTP/2 with Protocol Buffers | Binary serialization (Protobuf) is 6-10x smaller than JSON; eliminates parsing CPU overhead; binary framing multiplexes over a single persistent TCP socket. |
| **Gateway → Redis** | RESP3 Binary / Redis Protocol | Sub-millisecond atomic script execution for distributed concurrency control. |
| **Gateway → Customer Servers** | Webhook POST with HMAC-SHA256 | Asynchronous event push alerting with cryptographic proof of origin and tamper protection. |

---

## 4. Monorepo & Multi-Module Project Structure

The project is structured as a single Git repository containing decoupled Gradle/Maven modules:

* `gateway-common` : Shared DTOs, protobuf definitions, and crypto utilities.
* `model-router-service` : Standalone gRPC service responsible for provider routing and health.
* `api-gateway` : The public edge proxy (REST, SSE, GraphQL, JWT, Redis, Webhooks).
* `mock-webhook-receiver` : Standalone test verification harness simulating client webhook endpoints.

```text
llm-api-gateway/
├── .gitignore
├── README.md
├── docker-compose.yml
├── gradlew
├── gradlew.bat
├── settings.gradle
├── build.gradle
├── docker/
│   ├── postgres/
│   │   └── init.sql
│   └── redis/
│       └── redis.conf
├── gateway-common/
│   ├── build.gradle
│   └── src/
│       └── main/
│           ├── proto/
│           │   └── model_router.proto
│           └── java/com/gateway/common/
│               ├── crypto/
│               │   └── HmacSigner.java
│               ├── dto/
│               │   └── ProblemDetailDTO.java
│               └── exception/
│                   └── GatewayException.java
├── model-router-service/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── java/com/gateway/router/
│       │   │   ├── ModelRouterApplication.java
│       │   │   ├── config/
│       │   │   │   └── GrpcServerConfig.java
│       │   │   └── service/
│       │   │       ├── ModelRouterServiceImpl.java
│       │   │       └── ProviderHealthRegistry.java
│       │   └── resources/
│       │       └── application.yml
│       └── test/
│           └── java/com/gateway/router/
│               └── ModelRouterServiceTest.java
├── api-gateway/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── java/com/gateway/api/
│       │   │   ├── ApiGatewayApplication.java
│       │   │   ├── config/
│       │   │   │   ├── SecurityConfig.java
│       │   │   │   ├── RedisConfig.java
│       │   │   │   ├── GrpcClientConfig.java
│       │   │   │   └── GraphQLConfig.java
│       │   │   ├── security/
│       │   │   │   ├── JwtTokenProvider.java
│       │   │   │   ├── JwtAuthenticationFilter.java
│       │   │   │   └── UserPrincipal.java
│       │   │   ├── controller/
│       │   │   │   ├── AuthController.java
│       │   │   │   ├── ChatRestController.java
│       │   │   │   ├── ChatStreamController.java
│       │   │   │   └── DashboardGraphQLController.java
│       │   │   ├── interceptor/
│       │   │   │   ├── ApiVersionInterceptor.java
│       │   │   │   └── DeprecationHeaderFilter.java
│       │   │   ├── filter/
│       │   │   │   └── RateLimitingFilter.java
│       │   │   ├── service/
│       │   │   │   ├── LlmOrchestratorService.java
│       │   │   │   ├── FallbackRoutingEngine.java
│       │   │   │   ├── TokenAccountingService.java
│       │   │   │   ├── WebhookDispatcherService.java
│       │   │   │   └── client/
│       │   │   │       ├── OpenAiReactiveClient.java
│       │   │   │       └── AnthropicReactiveClient.java
│       │   │   ├── repository/
│       │   │   │   ├── UserRepository.java
│       │   │   │   ├── ApiKeyRepository.java
│       │   │   │   ├── UsageRecordRepository.java
│       │   │   │   └── WebhookSubscriptionRepository.java
│       │   │   ├── model/
│       │   │   │   ├── User.java
│       │   │   │   ├── ApiKey.java
│       │   │   │   ├── UsageRecord.java
│       │   │   │   └── WebhookSubscription.java
│       │   │   └── graphql/
│       │   │       ├── UserDataLoader.java
│       │   │       └── UsageBatchLoader.java
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── graphql/
│       │       │   └── schema.graphqls
│       │       └── lua/
│       │           └── token_bucket.lua
│       └── test/
│           └── java/com/gateway/api/
│               ├── security/JwtTokenProviderTest.java
│               ├── controller/ChatRestControllerTest.java
│               ├── stream/LlmFallbackStreamTest.java
│               ├── graphql/DashboardGraphQLTest.java
│               └── ratelimit/RedisRateLimiterTest.java
└── mock-webhook-receiver/
    ├── build.gradle
    └── src/
        └── main/
            ├── java/com/mock/receiver/
            │   ├── MockReceiverApplication.java
            │   └── WebhookConsumerController.java
            └── resources/
                └── application.yml
```

---

## 5. Git Workflow, Branching & Commit Standards

### 5.1 Repository Setup & Permissions

1. **Protected Main Branch:** Direct commits to `main` are rejected. All merges require a pull request, green test suite, and peer approval.
2. **Co-Authorship in Git Commits:** Every commit produced during pair programming must include the co-author trailer to give both engineers Git attribution:

```text
feat(grpc): define model router protobuf contract and build stubs

- Create model_router.proto with RouteRequest and RouteResponse
- Configure protobuf-gradle-plugin with protoc compiler 3.25.1
- Add unit tests for generated builder classes

Co-authored-by: Engineer One <eng1@gateway.io>
Co-authored-by: Engineer Two <eng2@gateway.io>
```

### 5.2 Branching Model

```text
main (production-ready)
│
├── sprint/01-grpc-contracts (Eng 1 + Eng 2)
│   ├── feat/proto-definition (Eng 1)
│   └── feat/grpc-client-stub (Eng 2)
│
├── sprint/02-auth-security
│   ├── feat/jwt-provider (Eng 1)
│   └── feat/jwt-filter-chain (Eng 2)
│
└── sprint/03-rest-sse-fallback
    ├── feat/rest-versioning (Eng 1)
    └── feat/webflux-sse-stream (Eng 2)
```

### 5.3 Commit Message Convention (Conventional Commits)

Format: `<type>(<scope>): <short description>`

* `feat` : New feature or endpoint.
* `fix` : Bug fix in logic or filter.
* `test` : Unit, integration, or load test suite.
* `refactor` : Structural code cleanup without functional changes.
* `perf` : Optimizations (e.g. Lua script tuning, connection pool sizing).

---

## 6. Low-Level Design (LLD) & Technical Specifications

### 6.1 Protocol Buffers & gRPC Schema

**File Path:** `gateway-common/src/main/proto/model_router.proto`

```protobuf
syntax = "proto3";

package com.gateway.router;

option java_multiple_files = true;
option java_package = "com.gateway.router.grpc";
option java_outer_classname = "ModelRouterProto";

service ModelRouterService {
  // Evaluates prompt metadata and system health to select the optimal provider
  rpc SelectRoute (RouteRequest) returns (RouteResponse);

  // Heartbeat check for model router instance health
  rpc CheckHealth (HealthCheckRequest) returns (HealthCheckResponse);
}

enum ProviderType {
  PROVIDER_UNSPECIFIED = 0;
  OPENAI = 1;
  ANTHROPIC = 2;
  GEMINI = 3;
}

message RouteRequest {
  string user_id = 1;
  string requested_model = 2;       // e.g. "auto-fast", "gpt-4o", "claude-3-5"
  int32 prompt_tokens_estimate = 3;
  string user_tier = 4;             // "FREE", "PRO", "ENTERPRISE"
}

message RouteResponse {
  ProviderType selected_provider = 1;
  string target_model_id = 2;         // e.g. "gpt-4o-2024-08-06"
  ProviderType fallback_provider = 3; // e.g. ANTHROPIC
  string fallback_model_id = 4;       // e.g. "claude-3-5-sonnet-20241022"
  int32 max_tokens_limit = 5;
  int32 timeout_millis = 6;
}

message HealthCheckRequest {
  string client_id = 1;
}

message HealthCheckResponse {
  enum ServingStatus {
    UNKNOWN = 0;
    SERVING = 1;
    NOT_SERVING = 2;
  }
  ServingStatus status = 1;
  map<string, bool> provider_availability = 2;
}
```

#### gRPC Channel Configuration Specifications

* **Transport:** Netty Channel with HTTP/2 framing.
* **Port:** `9090`.
* **Keep-Alive:** `keepAliveTime(30, TimeUnit.SECONDS)`, `keepAliveTimeout(5, TimeUnit.SECONDS)`.
* **Deadlines:** Every client stub invocation MUST enforce `.withDeadlineAfter(500, TimeUnit.MILLISECONDS)`.
* **Error Propagation:** The server maps internal exceptions to `io.grpc.StatusRuntimeException` with `Status.INVALID_ARGUMENT`, `Status.UNAVAILABLE`, or `Status.DEADLINE_EXCEEDED`.

---

### 6.2 Relational Database Schema (PostgreSQL)

**Database Name:** `gateway_db`  
**File Path:** `docker/postgres/init.sql`

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    tier VARCHAR(50) NOT NULL DEFAULT 'FREE', -- 'FREE', 'PRO', 'ENTERPRISE'
    monthly_budget_usd NUMERIC(10, 2) NOT NULL DEFAULT 50.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. API Keys Table (Hashed storage)
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_prefix VARCHAR(16) NOT NULL, -- First 8 chars for identification (e.g. "gw_live_")
    key_hash VARCHAR(255) NOT NULL UNIQUE, -- SHA-256 hash of full token
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_api_keys_hash ON api_keys(key_hash);

-- 3. Token Accounting & Usage Records
CREATE TABLE usage_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    model_id VARCHAR(100) NOT NULL,
    prompt_tokens INT NOT NULL,
    completion_tokens INT NOT NULL,
    total_tokens INT NOT NULL,
    estimated_cost_usd NUMERIC(10, 6) NOT NULL,
    was_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_usage_user_date ON usage_records(user_id, created_at DESC);

-- 4. Customer Webhook Subscriptions
CREATE TABLE webhook_subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_url VARCHAR(1024) NOT NULL,
    secret_key VARCHAR(255) NOT NULL, -- Shared secret for HMAC-SHA256
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Webhook Delivery Audit Log
CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    subscription_id UUID NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    response_status INT,
    attempt_count INT NOT NULL DEFAULT 1,
    delivery_status VARCHAR(50) NOT NULL, -- 'PENDING', 'SUCCESS', 'FAILED'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);
```

---

### 6.3 Redis Key Layout & Atomic Lua Rate Limiter

#### Key Namespacing Schema

| Key Pattern | Data Type | TTL | Description |
| :--- | :--- | :--- | :--- |
| `ratelimit:{userId}:tokens` | Hash | 60 sec | Stores `{ "last_updated": <epoch_ms>, "tokens": <float> }` |
| `usage:{userId}:{YYYY-MM}:spend` | String (Float) | 60 days | Running sum of monthly spend in USD |
| `idempotency:{key}` | String | 24 hours | Prevents duplicate webhook or payment processing |

#### Production Redis Lua Token Bucket Rate Limiter

**File Path:** `api-gateway/src/main/resources/lua/token_bucket.lua`

```lua
-- KEYS[1]: Rate limit key (e.g. ratelimit:usr_123:tokens)
-- ARGV[1]: Bucket capacity (max burst size, e.g. 10.0)
-- ARGV[2]: Refill rate per millisecond (e.g. 0.000166 for 10 tokens/min)
-- ARGV[3]: Current timestamp in milliseconds
-- ARGV[4]: Requested tokens (e.g. 1.0)
-- ARGV[5]: Key TTL in seconds (e.g. 60)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])
local ttl = tonumber(ARGV[5])

local data = redis.call("HMGET", key, "tokens", "last_updated")
local tokens = tonumber(data[1])
local last_updated = tonumber(data[2])

if tokens == nil then
    tokens = capacity
    last_updated = now
else
    local delta = math.max(0, now - last_updated)
    tokens = math.min(capacity, tokens + (delta * refill_rate))
    last_updated = now
end

if tokens >= requested then
    tokens = tokens - requested
    redis.call("HMSET", key, "tokens", tokens, "last_updated", last_updated)
    redis.call("EXPIRE", key, ttl)
    return { 1, math.floor(tokens) } -- 1 = Allowed, tokens remaining
else
    redis.call("HMSET", key, "tokens", tokens, "last_updated", last_updated)
    redis.call("EXPIRE", key, ttl)
    return { 0, math.floor(tokens) } -- 0 = Denied (429)
end
```

---

### 6.4 Stateless JWT Security Pipeline

#### Cryptographic Architecture

* **Algorithm:** HMAC-SHA256 (`HS256`) with a 256-bit cryptographically secure secret (configurable to `RS256` asymmetric keys).
* **Access Token TTL:** 15 minutes.
* **Refresh Token TTL:** 7 days (persisted with revocation tracking).
* **Standard Claims:**
  * `sub` : User UUID
  * `iss` : `llm-api-gateway`
  * `iat` : Timestamp
  * `exp` : Timestamp
  * `roles` : `["ROLE_USER", "ROLE_ADMIN"]`
  * `tier` : `FREE` | `PRO` | `ENTERPRISE`

#### Request Filter Execution Flow

```text
HTTP Request with 'Authorization: Bearer eyJhbGci...'
                  │
                  ▼
       JwtAuthenticationFilter (OncePerRequestFilter)
                  │
     Does header start with 'Bearer '?
                  ├── NO ──> Continue filter chain (Anonymous / 401 on protected endpoint)
                  │
                  └── YES ──> Extract token string
                                 │
                                 ▼
                       JwtTokenProvider.validateToken()
                       1. Verify signature with secret key
                       2. Check exp > Instant.now()
                                 │
                           Valid Token?
                                 ├── NO (Expired/Tampered) ──> Throw JwtException
                                 │                             Return RFC 7807 401 Unauthorized
                                 │
                                 └── YES ──> Extract Claims (userId, roles, tier)
                                                │
                                                ▼
                                         Construct UserPrincipal
                                         Set SecurityContextHolder:
                                         UsernamePasswordAuthenticationToken
                                                │
                                                ▼
                                         Proceed to Rate Limiting Filter
```

---

### 6.5 REST API, Error Envelopes & Versioning Engine

#### 1. Unified Chat Endpoint Specification

`POST /api/v1/chat/completions`

**Request Payload:**

```json
{
  "model": "auto-fast",
  "messages": [
    {
      "role": "user",
      "content": "Explain consensus algorithms in distributed systems."
    }
  ],
  "temperature": 0.7,
  "max_tokens": 1000
}
```

**Success Response (200 OK):**

```json
{
  "id": "chatcmpl-9b87df34-118c",
  "provider": "ANTHROPIC",
  "model": "claude-3-5-sonnet-20241022",
  "fallback_triggered": true,
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Consensus algorithms ensure multiple distributed nodes agree..."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 18,
    "completion_tokens": 142,
    "total_tokens": 160,
    "cost_usd": 0.00241
  }
}
```

#### 2. RFC 7807 Problem Details Error Envelope

Every failure (400, 401, 403, 429, 500) returns a consistent payload:

```json
{
  "type": "https://api.gateway.io/errors/rate-limit-exceeded",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Rate limit exceeded for tier FREE. Allowed: 10 req/min. Bucket resets in 23 seconds.",
  "instance": "/api/v1/chat/completions",
  "timestamp": "2026-10-18T10:15:30.124Z"
}
```

#### 3. Versioning & Deprecation Engine

The gateway supports both **URI Versioning** (`/api/v1/`) and **Header Versioning** (`X-API-VERSION: 2`). When clients invoke `/api/v1/`, the `DeprecationHeaderFilter` injects IETF standard deprecation headers:

```http
HTTP/1.1 200 OK
Deprecation: @1767225599
Sunset: Thu, 31 Dec 2026 23:59:59 GMT
Link: </api/v2/chat/completions>; rel="successor-version"
```

---

### 6.6 Reactive WebFlux SSE Streaming & Resilient Fallback

#### Endpoint Specification

`GET /api/v1/chat/stream?prompt={prompt}&model={model}`  
**Accept Header:** `text/event-stream`

#### Reactive Fallback Chain Implementation Logic

```java
public Flux<ServerSentEvent<String>> streamWithFallback(String prompt, RouteResponse route) {
    Flux<String> primaryStream = openAiClient.stream(prompt, route.getTargetModelId())
        .timeout(Duration.ofMillis(route.getTimeoutMillis()))
        .onErrorResume(ex -> {
            log.warn("Primary provider {} failed: {}. Engaging fallback: {}",
                route.getSelectedProvider(), ex.getMessage(), route.getFallbackProvider());
            return anthropicClient.stream(prompt, route.getFallbackModelId());
        });

    Flux<ServerSentEvent<String>> tokenFlux = primaryStream
        .map(token -> ServerSentEvent.<String>builder()
            .event("token")
            .data(token)
            .build());
            
    return tokenFlux;
}
```

### 6.7 GraphQL SDL Schema & DataLoader N+1 Fix

**File Path:** `api-gateway/src/main/resources/graphql/schema.graphqls`

```graphql
type Query {
  me: UserProfile!
  systemHealth: SystemHealth!
  teamMembersUsage: [UserProfile!]!
}

type Mutation {
  generateApiKey(name: String!): ApiKeyCreatedResponse!
  revokeApiKey(keyId: ID!): Boolean!
  updateWebhook(url: String!): WebhookSubscription!
}

type UserProfile {
  id: ID!
  email: String!
  tier: String!
  monthlyBudgetUsd: Float!
  currentSpendUsd: Float!
  usageSummary: UsageSummary!
  activeKeys: [ApiKeyInfo!]!
}

type UsageSummary {
  totalTokensConsumed: Int!
  providerBreakdown: [ProviderMetric!]!
}

type ProviderMetric {
  provider: String!
  tokenCount: Int!
  spendUsd: Float!
  percentageOfTotal: Float!
}

type ApiKeyInfo {
  id: ID!
  name: String!
  keyPrefix: String!
  createdAt: String!
  lastUsedAt: String
}

type ApiKeyCreatedResponse {
  apiKeyInfo: ApiKeyInfo!
  rawSecretKey: String! # Returned only once upon creation
}

type SystemHealth {
  gatewayStatus: String!
  modelRouterStatus: String!
}
```

#### Resolving the N+1 Query Problem with DataLoader

When a client requests `teamMembersUsage { id, email, usageSummary { totalTokensConsumed } }`:

* **Naive Implementation:** 1 query to fetch 50 users + 50 individual queries to fetch usage per user ($N + 1$).
* **DataLoader Solution:** Resolvers request keys via `DataLoader<UUID, UsageSummary>`. DataLoader batches all keys during an execution tick and issues **1 single query**:

```sql
SELECT user_id, SUM(total_tokens) as total_tokens, SUM(estimated_cost_usd) as total_cost
FROM usage_records
WHERE user_id IN (?, ?, ?, ... ?)
GROUP BY user_id;
```

---

### 6.8 Cryptographic Webhook Engine with Idempotency

#### 1. Outgoing Webhook Event Structure

```json
{
  "event_id": "evt_91b7d8c4-a521-4f12-87ce-00129bc8",
  "event_type": "budget.threshold_reached",
  "timestamp": 1792348500,
  "data": {
    "user_id": "usr_9a4f21",
    "budget_limit_usd": 50.00,
    "current_spend_usd": 50.12,
    "percentage": 100.24
  }
}
```

#### 2. HMAC-SHA256 Signature Generation

Every outbound webhook includes cryptographic proof in HTTP headers:

* `X-Gateway-Signature` : Hex-encoded HMAC-SHA256 signature calculated as `HMAC_SHA256(secret_key, timestamp + "." + payload_json)`.
* `X-Gateway-Timestamp` : Epoch timestamp to prevent replay attacks.
* `X-Idempotency-Key` : Unique UUID per event.

#### 3. Exponential Backoff Retry Mathematical Model

Failed deliveries (receiver returns 5xx or connection times out) are retried according to:

$$T_{\text{wait}} = \min(T_{\text{max}}, T_{\text{base}} \times 2^{\text{attempt}}) \pm \text{Jitter}$$

* $T_{\text{base}} = 1\text{ second}$
* **Maximum Retries:** 5 attempts
* **Jitter:** Random value between $-200\text{ms}$ and $+200\text{ms}$ to prevent the thundering herd problem.

---

## 7. Granular Collaborative Task Matrix (Micro-Sprints)

Every milestone is broken into synchronized micro-tasks where both engineers write code across every single layer.

### 🏁 Milestone 0: Environment & Multi-Module Skeleton

| Task # | Engineer 1 Task | Engineer 2 Task | Collaborative Handshake |
| :--- | :--- | :--- | :--- |
| **0.1** | Configure root `build.gradle` and `settings.gradle` declaring submodules: `gateway-common`, `model-router-service`, `api-gateway`, `mock-webhook-receiver`. | Create `docker-compose.yml` with PostgreSQL 16 (port 5432) and Redis 7 (port 6379) with healthchecks. | Run `docker compose up -d` and `./gradlew build` together; verify clean compile. |
| **0.2** | Write SQL initialization script `docker/postgres/init.sql` creating schemas and tables. | Write Spring `application.yml` files for database HikariCP and Lettuce Redis connections. | Boot `api-gateway` and verify PostgreSQL tables and Redis ping respond. |

### 🏁 Milestone 1: Protobuf & gRPC Model Selector

| Task # | Engineer 1 Task | Engineer 2 Task | Collaborative Handshake |
| :--- | :--- | :--- | :--- |
| **1.1** | Write Protobuf contract `model_router.proto` defining `RouteRequest`, `RouteResponse`, and `ModelRouterService`. | Configure `com.google.protobuf` Gradle plugin in `gateway-common` for Java code generation. | Run `./gradlew generateProto` together. Inspect generated builder classes in `build/generated/source/proto`. |
| **1.2** | Implement `ModelRouterServiceImpl` extending generated base class with model selection logic. | Write `ProviderHealthRegistry` simulating provider uptime, latency metrics, and failure states. | Start standalone gRPC server on port `9090`. Test using `grpcurl` or CLI harness. |
| **1.3** | Write `ModelRouterServiceTest` using gRPC in-process server and `StreamRecorder` assertions. | Implement `GrpcClientConfig` and `ModelRouterClient` in `api-gateway` with 500ms deadline interceptor. | Send 100 client requests from `api-gateway` to `model-router-service`; verify zero channel leaks. |

### 🏁 Milestone 2: Stateless JWT Authentication & Security

| Task # | Engineer 1 Task | Engineer 2 Task | Collaborative Handshake |
| :--- | :--- | :--- | :--- |
| **2.1** | Implement `JwtTokenProvider` using `io.jsonwebtoken` (claims: `sub`, `roles`, `tier`, `exp`, `iat`). | Write unit tests for `JwtTokenProvider`: expired token rejection, tampered signature detection. | Pair-debug raw JWT token: inspect Base64URL header, payload, and cryptographic signature. |
| **2.2** | Implement `AuthController` with endpoints `POST /api/v1/auth/register` and `POST /api/v1/auth/login`. | Implement `User` entity, `UserRepository`, and `BCryptPasswordEncoder` credential hashing. | Register user via curl, log in, and acquire a valid Bearer token. |
| **2.3** | Configure Spring Security 6 `SecurityFilterChain` with endpoint authorization rules. | Implement `JwtAuthenticationFilter` (`OncePerRequestFilter`) validating token and populating `SecurityContext`. | Test with no token (401), invalid token (401), valid token (200). |

### 🏁 Milestone 3: REST API Design & Versioning Strategy

| Task # | Engineer 1 Task | Engineer 2 Task | Collaborative Handshake |
| :--- | :--- | :--- | :--- |
| **3.1** | Build `ChatRequest` and `ChatResponse` DTOs with validation (`@NotBlank`, `@Min`, `@Max`). | Implement `@RestControllerAdvice` mapping exceptions to RFC 7807 `ProblemDetailDTO`. | Post invalid JSON; assert formatted RFC 7807 error envelope with 400 status. |
| **3.2** | Implement URI Path Versioning controller: `POST /api/v1/chat/completions`. | Implement Header Versioning routing based on `X-API-VERSION: 2`. | Test both V1 and V2 endpoints with identical payloads; observe version differential. |
| **3.3** | Write automated integration tests for version routing and content negotiation. | Implement `DeprecationHeaderFilter` attaching `Deprecation`, `Sunset`, and `Link` headers to V1. | Verify deprecation headers appear in response headers when calling `/v1/`. |

### 🏁 Milestone 4: Reactive SSE Streaming & Resilient Fallback

| Task # | Engineer 1 Task | Engineer 2 Task | Collaborative Handshake |
| :--- | :--- | :--- | :--- |
| **4.1** | Create `OpenAiReactiveClient` returning `Flux<String>` with configurable simulated 429 errors. | Create `AnthropicReactiveClient` returning reliable fallback `Flux<String>`. | Verify token emission rates (50ms intervals) using Project Reactor's `StepVerifier`. |
| **4.2** | Implement `FallbackRoutingEngine` using `.onErrorResume()` to bridge primary and secondary providers. | Write unit tests with `StepVerifier` asserting zero stream interruptions when primary fails midway. | Run fallback test; observe log output switching from OpenAI to Anthropic in real-time. |
| **4.3** | Implement SSE Controller `GET /api/v1/chat/stream` returning `Flux<ServerSentEvent<String>>`. | Implement periodic heartbeat generator emitting `: keep-alive` comments every 15s. | Test with `curl -N http://localhost:8080/api/v1/chat/stream`; watch words stream in terminal. |

### 🏁 Milestone 5: GraphQL Analytics Dashboard & DataLoader

| Task # | Engineer 1 Task | Engineer 2 Task | Collaborative Handshake |
| :--- | :--- | :--- | :--- |
| **5.1** | Author `schema.graphqls` defining `Query`, `Mutation`, `UserProfile`, and `UsageSummary`. | Configure Spring for GraphQL starter and implement root `@QueryMapping` for `me`. | Open GraphiQL UI at `http://localhost:8080/graphiql`; run query and inspect JSON shape. |
| **5.2** | Write naive `@SchemaMapping` causing N+1 database queries when fetching team members. | Implement `BatchLoaderRegistry` and `DataLoader` batching queries into `WHERE user_id IN (...)`. | Inspect SQL log output: observe query count drop from $N + 1$ down to 2. |
| **5.3** | Add GraphQL mutation `generateApiKey` creating hashed keys stored in PostgreSQL. | Implement query depth validator interceptor rejecting queries nested beyond 5 levels. | Fire recursive query; assert GraphQL engine rejects it before executing resolvers. |

### 🏁 Milestone 6: Webhooks & Distributed Rate Limiting

| Task # | Engineer 1 Task | Engineer 2 Task | Collaborative Handshake |
| :--- | :--- | :--- | :--- |
| **6.1** | Author atomic Redis Lua script `token_bucket.lua` with capacity and refill logic. | Build Spring `RateLimitingFilter` extracting user tier from JWT and invoking Lua script via Lettuce. | Run concurrent load test (15 parallel requests); assert exactly 10 succeed and 5 return 429. |
| **6.2** | Implement `HmacSigner` generating HMAC-SHA256 signatures with secret key. | Build `mock-webhook-receiver` application validating `X-Gateway-Signature` and timestamp. | Send webhook payload; verify mock receiver validates signature and logs receipt. |
| **6.3** | Build `WebhookDispatcherService` detecting budget breaches and queuing deliveries. | Implement exponential backoff retry loop with random jitter and idempotency key headers. | Shut down mock receiver; trigger webhook; watch retries back off; bring receiver up; assert delivery. |

---

## 8. End-to-End Verification, Curl Commands & Testing

### 8.1 Verification Script Matrix

#### 1. Register & Obtain JWT

```bash
# Register User
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"developer@enterprise.io","password":"SecurePassword123!"}'

# Login & Extract Token
JWT_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"developer@enterprise.io","password":"SecurePassword123!"}' | jq -r .token)

echo "JWT: $JWT_TOKEN"
```

#### 2. Test Rate Limiter (Fire 15 Requests in Parallel)

```bash
for i in {1..15}; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/chat/completions \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"model":"auto-fast","messages":[{"role":"user","content":"ping"}]}' &
done
wait
# Expected output: 10 responses with 200, 5 responses with 429
```

#### 3. Test Real-time SSE Token Streaming

```bash
curl -N -X GET "http://localhost:8080/api/v1/chat/stream?prompt=Explain+distributed+transactions&model=auto-fast" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Accept: text/event-stream"
```

#### 4. Test GraphQL Query with Zero Over-fetching

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "query { me { email tier monthlyBudgetUsd currentSpendUsd usageSummary { totalTokensConsumed } } }"
  }' | jq .
```

#### 5. Verify Webhook Signature via HMAC-SHA256

```bash
# In mock-webhook-receiver logs:
# Assert: "200 OK - Signature Verified for event evt_... IdempotencyKey=..."
```

---

## 9. Interview Readiness & Defense Guide

When defending this architecture in Senior / SDE2 backend interviews, both engineers must be prepared to answer:

#### Q1: What is the architectural difference between Authentication and Authorization in your gateway?

* **Authentication (AuthN):** Proving identity. Handled by `JwtAuthenticationFilter` by cryptographically validating the token's digital signature and expiration.
* **Authorization (AuthZ):** Determining permissions. Handled at the endpoint/method level using claims extracted from the token (`role: ROLE_ADMIN`, `tier: ENTERPRISE`).

#### Q2: How does JWT validation work without a database lookup on every incoming request?

* The token is a self-contained digital credential signed using a shared secret (`HS256`) or private key (`RS256`).
* The gateway verifies the signature mathematically using CPU cycles in memory.
* Because claims (`userId`, `tier`, `roles`) reside inside the decrypted payload, the gateway establishes context with zero database I/O.

#### Q3: Why choose gRPC over REST for internal communication between the Gateway and the Model Router?

* **Binary Serialization:** Protocol Buffers serialize into compact binary tags, eliminating ASCII parsing overhead.
* **HTTP/2 Multiplexing:** Thousands of concurrent RPC calls travel over a single long-lived TCP connection, avoiding head-of-line blocking and repeated TLS handshakes.
* **Type Safety & Contract Sharing:** The `.proto` file is a strict contract shared across services, eliminating schema drift.

#### Q4: Why is a naive Redis increment (`INCR`) insufficient for distributed rate limiting, and how does Lua solve it?

* A naive check-then-set requires multiple round-trips (`GET` current tokens $\rightarrow$ calculate in Java $\rightarrow$ `SET` new tokens). Under high concurrency, race conditions occur between the read and write steps.
* Redis executes Lua scripts atomically in a single thread, ensuring that token calculation, deduction, and expiration occur in one uninterruptible step.

#### Q5: What makes an API endpoint idempotent and why is it critical for webhooks?

* An endpoint is idempotent if executing it multiple times produces the identical side effect as executing it once.
* Network glitches or timeouts can cause the gateway to retry webhook deliveries. Without an `X-Idempotency-Key` and receiver-side deduplication, the customer's system might charge a credit card or increment an account balance multiple times.

---

*Specification authored for pair programming teams. Follow the micro-sprints in order; do not skip collaborative handshakes.*
