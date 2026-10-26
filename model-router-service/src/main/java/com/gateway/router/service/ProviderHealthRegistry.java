
package com.gateway.router.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProviderHealthRegistry {

    public enum ProviderStatus {
        HEALTHY,
        DEGRADED,
        UNAVAILABLE
    }

    // Immutable snapshot returned to callers.
    public record ProviderHealth(
            String providerId,
            double uptimePercent,
            double averageLatencyMs,
            long successfulRequests,
            long failedRequests,
            ProviderStatus status
    ) {}

    // Internal mutable state.
    private static class ProviderMetrics {
        private final String providerId;

        private long successfulRequests;
        private long failedRequests;
        private long consecutiveFailures;
        private double totalLatencyMs;
        private ProviderStatus status;

        private ProviderMetrics(
                String providerId,
                long successes,
                long failures,
                double averageLatencyMs,
                ProviderStatus status
        ) {
            this.providerId = providerId;
            this.successfulRequests = successes;
            this.failedRequests = failures;
            this.totalLatencyMs = averageLatencyMs * successes;
            this.status = status;
        }
    }

    private final Map<String, ProviderMetrics> providers =
            new HashMap<>();

    public ProviderHealthRegistry() {
        // Synthetic baseline metrics for development and testing.
        register("openai", 990, 10, 180,
                ProviderStatus.HEALTHY);

        register("anthropic", 985, 15, 250,
                ProviderStatus.HEALTHY);

        register("gemini", 900, 100, 900,
                ProviderStatus.DEGRADED);
    }

    private void register(
            String id,
            long successes,
            long failures,
            double averageLatencyMs,
            ProviderStatus status
    ) {
        providers.put(id, new ProviderMetrics(
                id, successes, failures, averageLatencyMs, status
        ));
    }

    public synchronized ProviderHealth getHealth(String providerId) {
        return snapshot(requireProvider(providerId));
    }

    public synchronized List<ProviderHealth> getAllHealth() {
        List<ProviderHealth> result = new ArrayList<>();

        for (ProviderMetrics metrics : providers.values()) {
            result.add(snapshot(metrics));
        }

        result.sort(Comparator.comparing(ProviderHealth::providerId));
        return List.copyOf(result);
    }

    public synchronized void recordSuccess(
            String providerId,
            double latencyMs
    ) {
        if (!Double.isFinite(latencyMs) || latencyMs < 0) {
            throw new IllegalArgumentException(
                    "Latency must be a finite, non-negative number"
            );
        }

        ProviderMetrics metrics = requireProvider(providerId);

        metrics.successfulRequests++;
        metrics.totalLatencyMs += latencyMs;
        metrics.consecutiveFailures = 0;

        metrics.status = latencyMs > 1000
                ? ProviderStatus.DEGRADED
                : ProviderStatus.HEALTHY;
    }

    public synchronized void recordFailure(String providerId) {
        ProviderMetrics metrics = requireProvider(providerId);

        metrics.failedRequests++;
        metrics.consecutiveFailures++;

        metrics.status = metrics.consecutiveFailures >= 3
                ? ProviderStatus.UNAVAILABLE
                : ProviderStatus.DEGRADED;
    }

    // Useful for deterministic failure-state tests.
    public synchronized void setStatus(
            String providerId,
            ProviderStatus status
    ) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }

        requireProvider(providerId).status = status;
    }

    private ProviderMetrics requireProvider(String providerId) {
        ProviderMetrics metrics = providers.get(providerId);

        if (metrics == null) {
            throw new IllegalArgumentException(
                    "Unknown provider: " + providerId
            );
        }

        return metrics;
    }

    private ProviderHealth snapshot(ProviderMetrics metrics) {
        long totalRequests =
                metrics.successfulRequests + metrics.failedRequests;

        double uptime = totalRequests == 0
                ? 100.0
                : metrics.successfulRequests * 100.0 / totalRequests;

        double averageLatency = metrics.successfulRequests == 0
                ? 0.0
                : metrics.totalLatencyMs / metrics.successfulRequests;

        return new ProviderHealth(
                metrics.providerId,
                uptime,
                averageLatency,
                metrics.successfulRequests,
                metrics.failedRequests,
                metrics.status
        );
    }
}
