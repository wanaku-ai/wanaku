package ai.wanaku.backend.bridge.transports.grpc;

import java.util.Iterator;
import java.util.Objects;
import org.jboss.logging.Logger;
import io.grpc.ManagedChannel;
import ai.wanaku.backend.bridge.InvokerBridge;
import ai.wanaku.backend.bridge.InvokerToolExecutor;
import ai.wanaku.backend.bridge.ProvisioningService;
import ai.wanaku.backend.bridge.ResourceAcquirerBridge;
import ai.wanaku.backend.bridge.WanakuBridgeTransport;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.CodeExecutionReply;
import ai.wanaku.core.exchange.v1.CodeExecutionRequest;
import ai.wanaku.core.exchange.v1.CodeExecutorGrpc;
import ai.wanaku.core.exchange.v1.Configuration;
import ai.wanaku.core.exchange.v1.PayloadType;
import ai.wanaku.core.exchange.v1.ResourceAcquirerGrpc;
import ai.wanaku.core.exchange.v1.ResourceReply;
import ai.wanaku.core.exchange.v1.ResourceRequest;
import ai.wanaku.core.exchange.v1.Secret;
import ai.wanaku.core.exchange.v1.ToolInvokeReply;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;
import ai.wanaku.core.exchange.v1.ToolInvokerGrpc;

/**
 * Encapsulates all gRPC transport operations for bridge implementations.
 * <p>
 * This class provides a centralized component for handling gRPC communication,
 * separating transport concerns from business logic in bridge classes. It manages:
 * <ul>
 *   <li>Channel creation and lifecycle via {@link GrpcChannelManager}</li>
 *   <li>Configuration and secret provisioning via {@link ProvisioningService}</li>
 *   <li>Tool invocation via gRPC stubs</li>
 *   <li>Resource acquisition via gRPC stubs</li>
 * </ul>
 * <p>
 * By using composition instead of inheritance, bridges can delegate transport
 * operations to this class while maintaining their own business logic. This
 * design makes it easier to test bridges independently and potentially support
 * alternative transport mechanisms in the future.
 *
 * @see InvokerBridge
 * @see ResourceAcquirerBridge
 * @see InvokerToolExecutor
 */
public class GrpcTransport implements WanakuBridgeTransport {
    private static final Logger LOG = Logger.getLogger(GrpcTransport.class);

    private final GrpcChannelManager channelManager;
    private final ProvisioningService provisioningService;

    /**
     * Creates a new GrpcTransport with default channel manager and provisioning service.
     */
    public GrpcTransport() {
        this.channelManager = new GrpcChannelManager();
        this.provisioningService = new ProvisioningService();
    }

    /**
     * Creates a new GrpcTransport with custom channel manager and provisioning service.
     * <p>
     * This constructor is primarily intended for testing purposes, allowing
     * injection of mock or custom implementations.
     *
     * @param channelManager the channel manager for creating gRPC channels
     * @param provisioningService the provisioning service for handling provisioning operations
     */
    GrpcTransport(GrpcChannelManager channelManager, ProvisioningService provisioningService) {
        this.channelManager = channelManager;
        this.provisioningService = provisioningService;
    }

    /**
     * Creates a new gRPC channel for the specified service.
     * <p>
     * This method delegates to the channel manager to create a channel
     * with consistent configuration.
     *
     * @param service the service target to connect to
     * @return a new managed channel
     */
    public ManagedChannel createChannel(ServiceTarget service) {
        return channelManager.createChannel(service);
    }

    /**
     * Provisions configuration and secrets to a remote service.
     * <p>
     * This method handles the complete provisioning workflow:
     * <ol>
     *   <li>Creates a gRPC channel to the service</li>
     *   <li>Builds configuration and secret objects</li>
     *   <li>Sends the provisioning request</li>
     *   <li>Returns a reference to the provisioned resources</li>
     * </ol>
     *
     * @param name the name of the configuration/secret
     * @param configData the configuration data (may be null)
     * @param secretsData the secrets data (may be null)
     * @param service the target service
     * @return a provisioning reference with URIs and properties
     */
    @Override
    public ProvisioningReference provision(String name, String configData, String secretsData, ServiceTarget service) {

        LOG.debugf("Provisioning '%s' to service: %s", name, service.toAddress());

        ManagedChannel channel = createChannel(service);

        Configuration cfg = Configuration.newBuilder()
                .setType(PayloadType.PAYLOAD_TYPE_BUILTIN)
                .setName(name)
                .setPayload(Objects.requireNonNullElse(configData, ""))
                .build();

        Secret secret = Secret.newBuilder()
                .setType(PayloadType.PAYLOAD_TYPE_BUILTIN)
                .setName(name)
                .setPayload(Objects.requireNonNullElse(secretsData, ""))
                .build();

        return provisioningService.provision(cfg, secret, channel, service);
    }

    /**
     * Invokes a tool on a remote service via gRPC.
     * <p>
     * This method creates a channel, builds a gRPC stub, and invokes the tool
     * with the provided request. It handles connection errors and converts them
     * to appropriate exceptions.
     *
     * @param request the tool invocation request
     * @param service the target service
     * @return the tool invocation reply from the remote service
     * @throws ServiceUnavailableException if the service cannot be reached
     */
    @Override
    public ToolInvokeReply invokeTool(ToolInvokeRequest request, ServiceTarget service) {
        LOG.debugf("Invoking tool on service: %s", service.toAddress());

        ManagedChannel channel = createChannel(service);

        try {
            ToolInvokerGrpc.ToolInvokerBlockingStub blockingStub = ToolInvokerGrpc.newBlockingStub(channel);
            return blockingStub.invokeTool(request);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to invoke tool on service: %s", service.toAddress());
            throw ServiceUnavailableException.forAddress(service.toAddress());
        }
    }

    /**
     * Acquires a resource from a remote service via gRPC.
     * <p>
     * This method creates a channel, builds a gRPC stub, and acquires the resource
     * with the provided request. It handles connection errors and converts them
     * to appropriate exceptions.
     *
     * @param request the resource acquisition request
     * @param service the target service
     * @return the resource reply from the remote service
     * @throws ServiceUnavailableException if the service cannot be reached
     */
    @Override
    public ResourceReply acquireResource(ResourceRequest request, ServiceTarget service) {
        LOG.debugf("Acquiring resource from service: %s", service.toAddress());

        ManagedChannel channel = createChannel(service);

        try {
            ResourceAcquirerGrpc.ResourceAcquirerBlockingStub blockingStub =
                    ResourceAcquirerGrpc.newBlockingStub(channel);
            return blockingStub.resourceAcquire(request);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to acquire resource from service: %s", service.toAddress());
            throw ServiceUnavailableException.forAddress(service.toAddress());
        }
    }

    /**
     * Executes code on a remote code execution service via gRPC streaming.
     * <p>
     * This method creates a channel, builds a gRPC stub, and initiates a streaming
     * code execution. The returned iterator allows the caller to consume execution
     * output as it arrives from the remote service.
     *
     * @param request the code execution request
     * @param service the target service
     * @return an iterator over the streaming code execution replies
     * @throws ServiceUnavailableException if the service cannot be reached
     */
    @Override
    public Iterator<CodeExecutionReply> executeCode(CodeExecutionRequest request, ServiceTarget service) {
        LOG.debugf("Executing code on service: %s", service.toAddress());

        ManagedChannel channel = createChannel(service);

        try {
            CodeExecutorGrpc.CodeExecutorBlockingStub blockingStub = CodeExecutorGrpc.newBlockingStub(channel);
            return blockingStub.executeCode(request);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to execute code on service: %s", service.toAddress());
            throw ServiceUnavailableException.forAddress(service.toAddress());
        }
    }
}
