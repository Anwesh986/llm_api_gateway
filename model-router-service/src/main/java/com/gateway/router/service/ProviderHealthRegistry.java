package com.gateway.router.service;

import com.gateway.router.grpc.ProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@Component
public class ProviderHealthRegistry {

    private final Map<ProviderType, Boolean> availability =
            new EnumMap<>(ProviderType.class);

    private final Map<ProviderType, Integer> latencyMillis =
            new EnumMap<>(ProviderType.class);

    public ProviderHealthRegistry() {
        availability.put(ProviderType.OPENAI, true);
        availability.put(ProviderType.ANTHROPIC, true);
        availability.put(ProviderType.GEMINI, true);

        latencyMillis.put(ProviderType.OPENAI, 120);
        latencyMillis.put(ProviderType.ANTHROPIC, 180);
        latencyMillis.put(ProviderType.GEMINI, 150);
    }

    public synchronized boolean isAvailable(ProviderType provider) {
        return availability.getOrDefault(provider, false);
    }

    public synchronized int getLatencyMillis(ProviderType provider) {
        return latencyMillis.getOrDefault(provider, Integer.MAX_VALUE);
    }

    public synchronized void setAvailability(
            ProviderType provider,
            boolean available) {

        Objects.requireNonNull(provider, "provider must not be null");

        if (provider == ProviderType.PROVIDER_UNSPECIFIED) {
            throw new IllegalArgumentException(
                    "PROVIDER_UNSPECIFIED cannot be registered");
        }

        availability.put(provider, available);
    }

    public synchronized void setLatencyMillis(
            ProviderType provider,
            int latencyMillis) {

        Objects.requireNonNull(provider, "provider must not be null");

        if (provider == ProviderType.PROVIDER_UNSPECIFIED) {
            throw new IllegalArgumentException(
                    "PROVIDER_UNSPECIFIED cannot be registered");
        }

        if (latencyMillis < 0) {
            throw new IllegalArgumentException(
                    "latencyMillis must not be negative");
        }

        this.latencyMillis.put(provider, latencyMillis);
    }

    public synchronized Map<ProviderType, Boolean> getAvailabilitySnapshot() {
        return new EnumMap<>(availability);
    }

    public synchronized ProviderType findLowestLatencyAvailable() {
        return availability.keySet()
                .stream()
                .filter(this::isAvailable)
                .min((left, right) -> Integer.compare(
                        getLatencyMillis(left),
                        getLatencyMillis(right)))
                .orElse(ProviderType.PROVIDER_UNSPECIFIED);
    }
}
