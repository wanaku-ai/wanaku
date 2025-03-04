package ai.wanaku.routing.yaml.route.service;

import jakarta.inject.Inject;

import ai.wanaku.core.exchange.InquireReply;
import ai.wanaku.core.exchange.InquireRequest;
import ai.wanaku.core.exchange.Inquirer;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import ai.wanaku.core.exchange.InvocationDelegate;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvoker;

@GrpcService
public class InvocationService implements ToolInvoker, Inquirer {

    @Inject
    InvocationDelegate delegate;

    @Override
    public Uni<ToolInvokeReply> invokeTool(ToolInvokeRequest request) {
        return Uni.createFrom().item(() -> delegate.invoke(request));
    }

    @Override
    public Uni<InquireReply> inquire(InquireRequest request) {
        InquireReply reply = InquireReply.newBuilder()
                .putAllServiceConfigurations(delegate.serviceConfigurations())
                .putAllCredentialsConfigurations(delegate.credentialsConfigurations())
                .build();

        return Uni.createFrom().item(() -> reply);
    }
}
