
package com.gateway.api.client;

import com.gateway.router.grpc.HealthCheckRequest;
import com.gateway.router.grpc.HealthCheckResponse;
import com.gateway.router.grpc.RouteRequest;
import com.gateway.router.grpc.RouteResponse;
import com.gateway.router.grpc.ModelRouterServiceGrpc;
import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.stereotype.Component;

@Component
public class ModelRouterClient {

    private final ModelRouterServiceGrpc.ModelRouterServiceFutureStub stub;

    public ModelRouterClient(
            ModelRouterServiceGrpc.ModelRouterServiceFutureStub stub
    ) {
        this.stub = stub;
    }

    public ListenableFuture<RouteResponse> selectRoute(
            RouteRequest request
    ) {
        return stub.selectRoute(request);
    }

    public ListenableFuture<HealthCheckResponse> checkHealth(
            String clientId
    ) {
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
                .setClientId(clientId)
                .build();

        return stub.checkHealth(request);
    }
}
