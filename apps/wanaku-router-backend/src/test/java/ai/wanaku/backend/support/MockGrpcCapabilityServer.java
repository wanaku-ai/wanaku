package ai.wanaku.backend.support;

import java.io.IOException;
import java.util.List;
import org.jboss.logging.Logger;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import ai.wanaku.core.exchange.v1.HealthProbeGrpc;
import ai.wanaku.core.exchange.v1.HealthProbeReply;
import ai.wanaku.core.exchange.v1.HealthProbeRequest;
import ai.wanaku.core.exchange.v1.ProvisionReply;
import ai.wanaku.core.exchange.v1.ProvisionRequest;
import ai.wanaku.core.exchange.v1.ProvisionerGrpc;
import ai.wanaku.core.exchange.v1.ResourceAcquirerGrpc;
import ai.wanaku.core.exchange.v1.ResourceReply;
import ai.wanaku.core.exchange.v1.ResourceRequest;
import ai.wanaku.core.exchange.v1.RuntimeStatus;
import ai.wanaku.core.exchange.v1.ToolInvokeReply;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;
import ai.wanaku.core.exchange.v1.ToolInvokerGrpc;

/**
 * A standalone gRPC server that mocks both tool and resource capabilities.
 * Returns pre-configured responses for tool invocations and resource acquisitions.
 */
public class MockGrpcCapabilityServer {
    private static final Logger LOG = Logger.getLogger(MockGrpcCapabilityServer.class);

    private final List<String> responseContent;
    private Server server;

    public MockGrpcCapabilityServer(List<String> responseContent) {
        this.responseContent = responseContent;
    }

    /**
     * Starts the gRPC server on a random available port.
     *
     * @return the port the server is listening on
     * @throws IOException if the server fails to start
     */
    public int start() throws IOException {
        server = ServerBuilder.forPort(0)
                .addService(new MockToolInvoker())
                .addService(new MockResourceAcquirer())
                .addService(new MockProvisioner())
                .addService(new MockHealthProbe())
                .build()
                .start();

        int port = server.getPort();
        LOG.infof("Mock gRPC capability server started on port %d", port);
        return port;
    }

    public void stop() {
        if (server != null) {
            server.shutdownNow();
            LOG.info("Mock gRPC capability server stopped");
        }
    }

    private class MockToolInvoker extends ToolInvokerGrpc.ToolInvokerImplBase {
        @Override
        public void invokeTool(ToolInvokeRequest request, StreamObserver<ToolInvokeReply> responseObserver) {
            LOG.infof("Mock tool invoked with uri=%s", request.getUri());
            ToolInvokeReply reply =
                    ToolInvokeReply.newBuilder().addAllContent(responseContent).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    private class MockResourceAcquirer extends ResourceAcquirerGrpc.ResourceAcquirerImplBase {
        @Override
        public void resourceAcquire(ResourceRequest request, StreamObserver<ResourceReply> responseObserver) {
            LOG.infof("Mock resource acquired with location=%s, name=%s", request.getLocation(), request.getName());
            ResourceReply reply =
                    ResourceReply.newBuilder().addAllContent(responseContent).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    private class MockProvisioner extends ProvisionerGrpc.ProvisionerImplBase {
        @Override
        public void provision(ProvisionRequest request, StreamObserver<ProvisionReply> responseObserver) {
            LOG.info("Mock provisioner called");
            ProvisionReply reply = ProvisionReply.newBuilder()
                    .setConfigurationUri("file:///tmp/mock-config")
                    .setSecretUri("file:///tmp/mock-secret")
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    private class MockHealthProbe extends HealthProbeGrpc.HealthProbeImplBase {
        @Override
        public void getStatus(HealthProbeRequest request, StreamObserver<HealthProbeReply> responseObserver) {
            HealthProbeReply reply = HealthProbeReply.newBuilder()
                    .setStatus(RuntimeStatus.RUNTIME_STATUS_STARTED)
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }
}
