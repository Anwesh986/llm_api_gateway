package com.gateway.router;

import com.gateway.router.grpc.HealthCheckRequest;
import com.gateway.router.grpc.HealthCheckResponse;
import com.gateway.router.grpc.ModelRouterServiceGrpc;
import com.gateway.router.grpc.ProviderType;
import com.gateway.router.grpc.RouteRequest;
import com.gateway.router.grpc.RouteResponse;
import com.gateway.router.service.ModelRouterServiceImpl;
import com.gateway.router.service.ProviderHealthRegistry;
import io.grpc.Channel;
import io.grpc.Server;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.StreamRecorder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRouterServiceTest {

    private Server server;
    private Channel channel;
    private ModelRouterServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        String serverName = "model-router-" + UUID.randomUUID();

        service = new ModelRouterServiceImpl(
                new ProviderHealthRegistry());

        server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();

        channel = InProcessChannelBuilder
                .forName(serverName)
                .directExecutor()
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel instanceof io.grpc.ManagedChannel managedChannel) {
            managedChannel.shutdownNow();
            managedChannel.awaitTermination(5, TimeUnit.SECONDS);
        }

        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void selectRouteReturnsHealthyProvider() {
        ModelRouterServiceGrpc.ModelRouterServiceStub stub =
                ModelRouterServiceGrpc.newStub(channel);

        StreamRecorder<RouteResponse> responseObserver =
                StreamRecorder.create();

        RouteRequest request = RouteRequest.newBuilder()
                .setUserId("user-123")
                .setRequestedModel("gpt-4o")
                .setPromptTokensEstimate(100)
                .setUserTier("PRO")
                .build();

        stub.selectRoute(request, responseObserver);

        List<RouteResponse> responses = responseObserver.getValues();

        assertEquals(1, responses.size());
        assertEquals(
                ProviderType.OPENAI,
                responses.get(0).getSelectedProvider());
        assertEquals("gpt-4o", responses.get(0).getTargetModelId());
        assertEquals(8_192, responses.get(0).getMaxTokensLimit());
        assertTrue(responseObserver.getError() == null);
    }

    @Test
    void checkHealthReturnsProviderAvailability() {
        ModelRouterServiceGrpc.ModelRouterServiceStub stub =
                ModelRouterServiceGrpc.newStub(channel);

        StreamRecorder<HealthCheckResponse> responseObserver =
                StreamRecorder.create();

        HealthCheckRequest request = HealthCheckRequest.newBuilder()
                .setClientId("api-gateway")
                .build();

        stub.checkHealth(request, responseObserver);

        List<HealthCheckResponse> responses = responseObserver.getValues();

        assertEquals(1, responses.size());
        assertEquals(
                HealthCheckResponse.ServingStatus.SERVING,
                responses.get(0).getStatus());
        assertTrue(
                responses.get(0)
                        .getProviderAvailabilityOrDefault("OPENAI", false));
        assertTrue(responseObserver.getError() == null);
    }

    @Test
    void invalidRouteRequestReturnsInvalidArgument() {
        ModelRouterServiceGrpc.ModelRouterServiceStub stub =
                ModelRouterServiceGrpc.newStub(channel);

        StreamRecorder<RouteResponse> responseObserver =
                StreamRecorder.create();

        RouteRequest request = RouteRequest.newBuilder()
                .setUserId("user-123")
                .setPromptTokensEstimate(100)
                .setUserTier("PRO")
                .build();

        stub.selectRoute(request, responseObserver);

        assertTrue(responseObserver.getValues().isEmpty());
        assertEquals(
                io.grpc.Status.Code.INVALID_ARGUMENT,
                io.grpc.Status.fromThrowable(
                        responseObserver.getError()).getCode());
    }
}