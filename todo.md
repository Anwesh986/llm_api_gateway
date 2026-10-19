# LLM API Gateway — Complete Engineering Specification & Implementation Manual

**Document Type:** Production Architecture (HLD/LLD), Repository Layout, Collaborative Division & Delivery Guide
**Project Level:** Level 4 — API Design, Protocols & Distributed Communication
**Target Audience:** Engineer 1 & Engineer 2 (Collaborative Pair Programming Team)
**Target Stack:** Java 21, Spring Boot 3.3.x, Spring WebFlux, Spring Security 6, Spring for GraphQL, gRPC Java (Protobuf 3), PostgreSQL 16, Redis 7, Docker Compose

---

## 📑 Table of Contents

1. **Executive Summary & Problem Statement**
2. **High-Level Architecture (HLD)**
3. **Component Architecture & Protocol Boundaries**
4. **Monorepo & Multi-Module Project Structure**
5. **Git Workflow, Branching & Commit Standards**
6. **Low-Level Design (LLD) & Technical Specifications**
   * 6.1 Protocol Buffers & gRPC Schema
   * 6.2 Relational Database Schema (PostgreSQL)
   * 6.3 Redis Key Layout & Atomic Lua Rate Limiter
   * 6.4 Stateless JWT Security Pipeline
   * 6.5 REST API, Error Envelopes & Versioning Engine
   * 6.6 Reactive WebFlux SSE Streaming & Resilient Fallback
   * 6.7 GraphQL SDL Schema & DataLoader N+1 Fix
   * 6.8 Cryptographic Webhook Engine with Idempotency
7. **Granular Collaborative Task Matrix (Micro-Sprints)**
8. **End-to-End Verification, Curl Commands & Testing**
9. **Interview Readiness & Defense Guide**

---

## 1. Executive Summary & Problem Statement

### 1.1 The Industry Problem

Organizations building AI-powered applications face severe operational vulnerabilities when integrating directly with commercial LLM providers (OpenAI, Anthropic, Google Gemini):

1. **Vendor Outages & Strict Rate Limits:** Provider 429 (Too Many Requests) or 503 (Service Unavailable) outages completely halt consumer applications.
2. **Protocol & Payload Fragmentation:** Each provider requires proprietary SDKs, unique JSON payloads, and divergent streaming protocols.
3. **Runaway Financial Exposure:** Unmetered client requests result in unexpected API bills without real-time per-client token throttling or budget alert triggers.
4. **Network Inefficiencies:** Web dashboards over-fetch gigabytes of chat logs just to display billing metrics, while internal microservice routing suffers from slow JSON over HTTP/1.1 serialization.

### 1.2 The Gateway Solution

The LLM API Gateway acts as a unified reverse proxy and intelligent router sitting between client applications and downstream AI providers. It exposes:

*   **Stateless REST & Non-blocking SSE (Server-Sent Events)** for universal client ingestion.
*   **Sub-millisecond gRPC / Protobuf communication** for internal model routing and health checks.
*   **GraphQL with DataLoaders** for client analytics dashboards with zero over-fetching.
*   **Atomic Redis Token Bucket Rate Limiting** to protect upstream provider quotas.
*   **HMAC-SHA256 Signed Outbound Webhooks** with exponential backoff retries for billing alerts.

---

## 2. High-Level Architecture (HLD)

### 2.1 System Topology Diagram

====================================================================================================
                                EXTERNAL CLIENT LAYER
Mobile Apps, React/Next.js Single Page Applications, External Backend Services, CLI Tools
====================================================================================================
        |                            |                            |
        | HTTP/1.1 or HTTP/2         | HTTP/1.1 (Accept: text/event-| HTTP POST /graphql
        | JSON: POST /api/v{1,2}/chat| stream): GET /api/v1/chat/stream|
        v                            v                            v
----------------------------------------------------------------------------------------------------
                                EDGE SERVICE: API GATEWAY (:8080)
----------------------------------------------------------------------------------------------------
|                                                                                                  |
|  [ 1. Security Filter Chain: JWT Authentication, Signature Check (HS256), SecurityContext    ]   |
|           |                                                                                      |
|           v                                                                                      |
|  [ 2. Rate Limiting Filter: Redis Atomic Lua Token Bucket Check (Tier-based: Free/Pro)       ]   |
|           |                                                                                      |
|           v                                                                                      |
|  [ 3. API Versioning & Deprecation Engine: URI Path (/v1/) vs Header (X-API-VERSION: 2)      ]   |
|           |                                                                                      |
|           v                                                                                      |
|  -----------------------------------           ---------------------------------------------     |
|  | ChatController (REST & SSE)     |           | DashboardGraphQLController                |     |
|  | - Reactive Flux Stream          |           | - SDL Queries & Mutations                 |     |
|  | - Heartbeat Ping Generator      |           | - DataLoader Batch Aggregators            |     |
|  -----------------------------------           ---------------------------------------------     |
----------------------------------------------------------------------------------------------------
            |                                                     |
            | gRPC over HTTP/2 (Binary Protobuf)                  | JPA / JDBC Pool
            v (:9090)                                             v
-------------------------------------------      ---------------------------------------------------
|       SERVICE 2: MODEL SELECTOR SERVICE |      |             POSTGRESQL 16 (:5432)               |
|                                         |      |                                                 |
| - ModelRouterServiceImpl (gRPC Server)  |      | - users & api_keys                              |
| - Provider Health State Registry        |      | - token_usage_records                           |
| - Dynamic Routing Matrix (Cost vs Latency)|    | - webhook_subscriptions & audit_logs            |
| - Deadlines & Channel Keep-Alive        |      ---------------------------------------------------
-------------------------------------------                             ^
            | Returns RouteDecision                                     |
            v                                                           |
-----------------------------------------------------------             |
|         LLM PROVIDER DISPATCHER & RESILIENT FALLBACK    |             |
|                                                         |             |
| Primary: OpenAI GPT-4o --(onErrorResume: 429/5xx)--> Anthropic      |
|                                              Claude 3.5 |             |
-----------------------------------------------------------             |
            | Asynchronously Records Tokens & Spend                     |
            v                                                           |
-------------------------------------------      ---------------------------------------------------
|           REDIS 7 (:6379)               |      |            WEBHOOK DISPATCHER WORKER            |
|                                         |      |                                                 |
| - Atomic Token Bucket Counters          |      | - Scheduled / Async Thread Pool                 |
| - Real-time Monthly Usage Counters      |<-----| Threshold- Exceeds 80% / 100% Budget Notification|
| - Distributed Idempotency Locks         | Check| - HMAC-SHA256 Payload Signature                 |
-------------------------------------------      | - Exponential Backoff Retries + Jitter          |
                                                 ---------------------------------------------------
                                                                        | HTTP POST
                                                                        v
                                                            [ Customer Webhook Listener ]

---

## 3. Component Architecture & Protocol Boundaries

### 3.1 Edge Layer vs Internal Protocol Split

| Boundary | Protocols Used | Rationale |
| :--- | :--- | :--- |
| **Client -> Gateway (Ingestion)** | REST (HTTP/1.1, HTTP/2), JSON | Standardized client compatibility. Zero third-party runtime dependencies for consumer apps. |
| **Client -> Gateway (Streaming)** | Server-Sent Events (SSE), UTF-8 text streams | Long-lived unidirectional stream supported natively by browser `EventSource` and curl without WebSocket upgrade overhead. |
| **Client -> Gateway (Analytics)** | GraphQL over HTTP POST | Allows developer consoles to query complex hierarchical billing and metric trees without N+1 network requests or over-fetching. |
| **Gateway -> Model Selector (Internal)** | gRPC over HTTP/2 with Protocol Buffers | Binary serialization (Protobuf) is 6-10x smaller than JSON; eliminates parsing CPU overhead; binary framing multiplexes over a single persistent TCP socket. |
| **Gateway -> Redis** | RESP3 Binary / Redis Protocol | Sub-millisecond atomic script execution for distributed concurrency control. |
| **Gateway -> Customer Servers** | Webhook POST with HMAC-SHA256 | Asynchronous event push alerting with cryptographic proof of origin and tamper protection. |

---

## 4. Monorepo & Multi-Module Project Structure

The project is structured as a single Git repository containing decoupled Gradle/Maven modules:

*   `gateway-common` : Shared DTOs, protobuf definitions, and crypto utilities.
*   `model-router-service` : Standalone gRPC service responsible for provider routing and health.
*   `api-gateway` : The public edge proxy (REST, SSE, GraphQL, JWT, Redis, Webhooks).
*   `mock-webhook-receiver` : Standalone test verification harness simulating client webhook endpoints.

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
│       │   │   │   └── GraphQlConfig.java
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
│       │   │   │   └── WebhookDispatcherService.java
│       │   │   │   └── client/
│       │   │   │       ├── OpenAIReactiveClient.java
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

---

## 5. Git Workflow, Branching & Commit Standards

### 5.1 Repository Setup & Permissions

1. **Protected Main Branch:** Direct commits to `main` are rejected. All merges require a pull request, green test suite, and peer approval.
2. **Co-Authorship in Git Commits:** Every commit produced during pair programming must include the co-author trailer to give both engineers Git attribution:

feat(grpc): define model router protobuf contract and build stubs

- Create model_router.proto with RouteRequest and RouteResponse
- Configure protobuf-gradle-plugin with protoc compiler 3.25.1
- Add unit tests for generated builder classes

Co-authored-by: Engineer One <eng1@gateway.io>
Co-authored-by: Engineer Two <eng2@gateway.io>

### 5.2 Branching Model

main (production-ready)
|
├── sprint/01-grpc-contracts (Eng 1 + Eng 2)
│   ├── feat/proto-definition (Eng 1)
│   └── feat/grpc-client-stub (Eng 2)
|
├── sprint/02-auth-security
│   ├── feat/jwt-provider (Eng 1)
│   └── feat/jwt-filter-chain (Eng 2)
|
└── sprint/03-rest-sse-fallback
    ├── feat/rest-versioning (Eng 1)
    └── feat/webflux-sse-stream (Eng 2)

### 5.3 Commit Message Convention (Conventional Commits)

Format: `<type>(<scope>): <short description>`

*   `feat` : New feature or endpoint.
*   `fix` : Bug fix in logic or filter.
*   `test` : Unit, integration, or load test suite.
*   `refactor` : Structural code cleanup without functional changes.
*   `perf` : Optimizations (e.g. Lua script tuning, connection pool sizing).

---

## 6. Low-Level Design (LLD) & Technical Specifications

### 6.1 Protocol Buffers & gRPC Schema

**File Path:** `gateway-common/src/main/proto/model_router.proto`

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
  string target_model_id = 2;       // e.g. "gpt-4o-2024-08-06"
  ProviderType fallback_provider = 3; // e.g. ANTHROPIC
  string fallback_model_id = 4;     // e.g. "claude-3-5-sonnet-20241022"
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

**gRPC Channel Configuration Specifications**

*   **Transport:** Netty Channel with HTTP/2 framing.
*   **Port:** `9090`.
*   **Keep-Alive:** `keepAliveTime(30, TimeUnit.SECONDS)`, `keepAliveTimeout(5, TimeUnit.SECONDS)`.
*   **Deadlines:** Every client stub invocation MUST enforce `.withDeadlineAfter(500, TimeUnit.MILLISECONDS)`.
*   **Error Propagation:** The server maps internal exceptions to `io.grpc.StatusRuntimeException` with `Status.INVALID_ARGUMENT`, `Status.UNAVAILABLE`, or `Status.DEADLINE_EXCEEDED`.

### 6.2 Relational Database Schema (PostgreSQL)

**Database Name:** `gateway_db`
**File Path:** `docker/postgres/init.sql`

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

### 6.3 Redis Key Layout & Atomic Lua Rate Limiter

**Key Namespacing Schema**

| Key Pattern | Data Type | TTL | Description |
| :--- | :--- | :--- | :--- |
| `ratelimit:{userId}:tokens` | Hash | 60 sec | Stores `{ "last_updated": <epoch_ms>, "tokens": <float> }` |
| `usage:{userId}:{YYYY-MM}:spend` | String (Float) | 60 days | Running sum of monthly spend in USD |
| `idempotency:{key}` | String | 24 hours | Prevents duplicate webhook or payment processing |

**Production Redis Lua Token Bucket Rate Limiter**

**File Path:** `api-gateway/src/main/resources/lua/token_bucket.lua`

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

### 6.4 Stateless JWT Security Pipeline

**Cryptographic Architecture**

*   **Algorithm:** HMAC-SHA256 (`HS256`) with a 256-bit cryptographically secure secret (configurable to `RS256` asymmetric keys).
*   **Access Token TTL:** 15 minutes.
*   **Refresh Token TTL:** 7 days (persisted with revocation tracking).
*   **Standard Claims:**
    *   `sub`: User UUID
    *   `iss`: `llm-api-gateway`
    *   `iat`: Timestamp
    *   `exp`: Timestamp
    *   `roles`: `["ROLE_USER", "ROLE_ADMIN"]`
    *   `tier`: `FREE` | `PRO` | `ENTERPRISE`

**Request Filter Execution Flow**

HTTP Request with "Authorization: Bearer eyJhbGci..."
                  |
                  v
       JwtAuthenticationFilter (OncePerRequestFilter)
                  |
     Does header start with "Bearer "?
                  ├─ NO ─> Continue filter chain (Anonymous / 401 on protected endpoint)
                  |
                  └─ YES ─> Extract token string
                                 |
                                 v
                       JwtTokenProvider.validateToken()
                       1. Verify signature with secret key
                       2. Check exp > Instant.now()
                                 |
                           Valid Token?
                                 ├─ NO (Expired/Tampered) ─> Throw JwtException
                                 |                           Return RFC 7807 401 Unauthorized
                                 |
                                 └─ YES ─> Extract Claims (userId, roles, tier)
                                                |
                                                v
                                         Construct UserPrincipal
                                         Set SecurityContextHolder:
                                         UsernamePasswordAuthenticationToken
                                                |
                                                v
                                         Proceed to Rate Limiting Filter

### 6.5 REST API, Error Envelopes & Versioning Engine

**1. Unified Chat Endpoint Specification**

`POST /api/v1/chat/completions`

**Request Payload:**

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

**Success Response (200 OK):**

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

**2. RFC 7807 Problem Details Error Envelope**

Every failure (400, 401, 403, 429, 500) returns a consistent payload:

{
  "type": "https://api.gateway.io/errors/rate-limit-exceeded",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Rate limit exceeded for tier FREE. Allowed: 10 req/min. Bucket resets in 23 seconds.",
  "instance": "/api/v1/chat/completions",
  "timestamp": "2026-10-18T10:15:30.124Z"
}

**3. Versioning & Deprecation Engine**

The gateway supports both URI Versioning (`/api/v1/`) and Header Versioning (`X-API-VERSION: 2`). When clients invoke `/api/v1/`, the `DeprecationHeaderFilter` injects IETF standard deprecation headers:

HTTP/1.1 200 OK
Deprecation: @1767225599
Sunset: Thu, 31 Dec 2026 23:59:59 GMT
Link: </api/v2/chat/completions>; rel="successor-version"

### 6.6 Reactive WebFlux SSE Streaming & Resilient Fallback

**Endpoint Specification**

`GET /api/v1/chat/stream?prompt={prompt}&model={model}`
**Accept Header:** `text/event-stream`

**Reactive Fallback Chain Implementation Logic**

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
}