package com.gateway.router.service;

import com.gateway.router.grpc.HealthCheckRequest;
import com.gateway.router.grpc.HealthCheckResponse;
import com.gateway.router.grpc.ModelRouterServiceGrpc;
import com.gateway.router.grpc.ProviderType;
import com.gateway.router.grpc.RouteRequest;
import com.gateway.router.grpc.RouteResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Locale;
import java.util.Map;

@GrpcService
public class ModelRouterServiceImpl
        extends ModelRouterServiceGrpc.ModelRouterServiceImplBase {

    private final ProviderHealthRegistry healthRegistry;

    public ModelRouterServiceImpl(ProviderHealthRegistry healthRegistry) {
        this.healthRegistry = healthRegistry;
    }

    @Override
    public void selectRoute(
            RouteRequest request,
            StreamObserver<RouteResponse> responseObserver) {

        try {
            validateRouteRequest(request);

            ProviderType preferredProvider =
                    resolvePreferredProvider(request.getRequestedModel());

            ProviderType selectedProvider = selectAvailableProvider(
                    preferredProvider);

            if (selectedProvider == ProviderType.PROVIDER_UNSPECIFIED) {
                responseObserver.onError(
                        Status.UNAVAILABLE
                                .withDescription(
                                        "No healthy model provider is available")
                                .asRuntimeException());
                return;
            }

            ProviderType fallbackProvider =
                    findFallbackProvider(selectedProvider);

            RouteResponse.Builder response = RouteResponse.newBuilder()
                    .setSelectedProvider(selectedProvider)
                    .setTargetModelId(resolveTargetModel(
                            selectedProvider,
                            request.getRequestedModel()))
                    .setMaxTokensLimit(resolveMaxTokens(
                            request.getUserTier()))
                    .setTimeoutMillis(resolveTimeoutMillis(
                            selectedProvider));

            if (fallbackProvider != ProviderType.PROVIDER_UNSPECIFIED) {
                response
                        .setFallbackProvider(fallbackProvider)
                        .setFallbackModelId(resolveDefaultModel(
                                fallbackProvider));
            }

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException exception) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(exception.getMessage())
                            .asRuntimeException());
        }
    }

    @Override
    public void checkHealth(
            HealthCheckRequest request,
            StreamObserver<HealthCheckResponse> responseObserver) {

        Map<ProviderType, Boolean> availability =
                healthRegistry.getAvailabilitySnapshot();

        HealthCheckResponse.Builder response =
                HealthCheckResponse.newBuilder()
                        .setStatus(resolveServingStatus(availability));

        availability.forEach((provider, isAvailable) ->
                response.putProviderAvailability(
                        provider.name(),
                        isAvailable));

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    private void validateRouteRequest(RouteRequest request) {
        if (request.getRequestedModel().isBlank()) {
            throw new IllegalArgumentException(
                    "requested_model must not be blank");
        }

        if (request.getPromptTokensEstimate() < 0) {
            throw new IllegalArgumentException(
                    "prompt_tokens_estimate must not be negative");
        }
    }

    private ProviderType resolvePreferredProvider(String requestedModel) {
        String normalizedModel = requestedModel
                .trim()
                .toLowerCase(Locale.ROOT);

        if (normalizedModel.equals("auto-fast")) {
            return healthRegistry.findLowestLatencyAvailable();
        }

        if (normalizedModel.startsWith("gpt")
                || normalizedModel.contains("openai")) {
            return ProviderType.OPENAI;
        }

        if (normalizedModel.startsWith("claude")
                || normalizedModel.contains("anthropic")) {
            return ProviderType.ANTHROPIC;
        }

        if (normalizedModel.startsWith("gemini")
                || normalizedModel.contains("gemini")) {
            return ProviderType.GEMINI;
        }

        throw new IllegalArgumentException(
                "Unsupported requested_model: " + requestedModel);
    }

    private ProviderType selectAvailableProvider(
            ProviderType preferredProvider) {

        if (healthRegistry.isAvailable(preferredProvider)) {
            return preferredProvider;
        }

        return healthRegistry.findLowestLatencyAvailable();
    }

    private ProviderType findFallbackProvider(
            ProviderType selectedProvider) {

        return healthRegistry.getAvailabilitySnapshot()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey() != selectedProvider)
                .filter(Map.Entry::getValue)
                .min((left, right) -> Integer.compare(
                        healthRegistry.getLatencyMillis(left.getKey()),
                        healthRegistry.getLatencyMillis(right.getKey())))
                .map(Map.Entry::getKey)
                .orElse(ProviderType.PROVIDER_UNSPECIFIED);
    }

    private String resolveTargetModel(
            ProviderType provider,
            String requestedModel) {

        if (!requestedModel.equalsIgnoreCase("auto-fast")) {
            return requestedModel;
        }

        return resolveDefaultModel(provider);
    }

    private String resolveDefaultModel(ProviderType provider) {
        return switch (provider) {
            case OPENAI -> "gpt-4o";
            case ANTHROPIC -> "claude-3-5-sonnet";
            case GEMINI -> "gemini-1.5-pro";
            case PROVIDER_UNSPECIFIED ->
                    throw new IllegalArgumentException(
                            "Provider must be specified");
            default -> throw new IllegalArgumentException("Unexpected value: " + provider);
        };
    }

    private int resolveMaxTokens(String userTier) {
        return switch (userTier.trim().toUpperCase(Locale.ROOT)) {
            case "ENTERPRISE" -> 16_384;
            case "PRO" -> 8_192;
            case "FREE", "" -> 2_048;
            default -> throw new IllegalArgumentException(
                    "Unsupported user_tier: " + userTier);
        };
    }

    private int resolveTimeoutMillis(ProviderType provider) {
        return healthRegistry.getLatencyMillis(provider) + 380;
    }

    private HealthCheckResponse.ServingStatus resolveServingStatus(
            Map<ProviderType, Boolean> availability) {

        return availability.values()
                .stream()
                .anyMatch(Boolean.TRUE::equals)
                ? HealthCheckResponse.ServingStatus.SERVING
                : HealthCheckResponse.ServingStatus.NOT_SERVING;
    }
}