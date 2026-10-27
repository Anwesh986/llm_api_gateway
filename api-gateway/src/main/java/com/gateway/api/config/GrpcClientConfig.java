
package com.gateway.api.config;

import com.gateway.router.grpc.ModelRouterServiceGrpc;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel modelRouterChannel(
            @Value("${grpc.model-router.host:localhost}") String host,
            @Value("${grpc.model-router.port:9090}") int port
    ) {
        return ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext() // Local development only; use TLS in production.
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .intercept(new DeadlineClientInterceptor())
                .build();
    }

    @Bean
    public ModelRouterServiceGrpc.ModelRouterServiceFutureStub
    modelRouterFutureStub(ManagedChannel modelRouterChannel) {
        return ModelRouterServiceGrpc.newFutureStub(modelRouterChannel);
    }

    /**
     * Applies a 500 ms deadline to every RPC made through this channel.
     */
    static class DeadlineClientInterceptor implements ClientInterceptor {

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method,
                CallOptions callOptions,
                Channel next
        ) {
            CallOptions optionsWithDeadline =
                    callOptions.withDeadlineAfter(
                            500, TimeUnit.MILLISECONDS
                    );

            return next.newCall(method, optionsWithDeadline);
        }
    }
}
