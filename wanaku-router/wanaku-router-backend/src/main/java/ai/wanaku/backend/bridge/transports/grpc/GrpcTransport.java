package ai.wanaku.backend.bridge.transports.grpc;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import ai.wanaku.backend.bridge.InvokerBridge;
import ai.wanaku.backend.bridge.ResourceAcquirerBridge;
import ai.wanaku.backend.bridge.WanakuBridgeTransport;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.CodeExecutionReply;
import ai.wanaku.core.exchange.v1.CodeExecutionRequest;
import ai.wanaku.core.exchange.v1.CodeExecutorGrpc;
import ai.wanaku.core.exchange.v1.Configuration;
import ai.wanaku.core.exchange.v1.HealthProbeGrpc;
import ai.wanaku.core.exchange.v1.HealthProbeReply;
import ai.wanaku.core.exchange.v1.HealthProbeRequest;
import ai.wanaku.core.exchange.v1.PayloadType;
import ai.wanaku.core.exchange.v1.ProvisionReply;
import ai.wanaku.core.exchange.v1.ProvisionRequest;
import ai.wanaku.core.exchange.v1.ProvisionerGrpc;
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
 *   <li>Configuration and secret provisioning via gRPC</li>
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
 */
public class GrpcTransport implements WanakuBridgeTransport {
    private static final Logger LOG = Logger.getLogger(GrpcTransport.class);
    private static final String WANAKU_BRIDGE_GRPC_TRANSPORT_DEADLINE_SECONDS =
            "wanaku.bridge.grpc.transport.deadline-seconds";

    private final GrpcChannelManager channelManager;
    private final int deadlineSeconds;

    /**
     * Creates a new GrpcTransport with default channel manager.
     */
    public GrpcTransport() {
        this.channelManager = new GrpcChannelManager();

        deadlineSeconds =
                ConfigProvider.getConfig().getValue(WANAKU_BRIDGE_GRPC_TRANSPORT_DEADLINE_SECONDS, Integer.class);
    }

    /**
     * Creates a new GrpcTransport with a custom channel manager.
     * <p>
     * This constructor is primarily intended for testing purposes, allowing
     * injection of mock or custom implementations.
     *
     * @param channelManager the channel manager for creating gRPC channels
     */
    GrpcTransport(GrpcChannelManager channelManager) {
        this.channelManager = channelManager;

        deadlineSeconds =
                ConfigProvider.getConfig().getValue(WANAKU_BRIDGE_GRPC_TRANSPORT_DEADLINE_SECONDS, Integer.class);
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
        String safeName = Objects.requireNonNullElse(name, "");

        ManagedChannel channel = createChannel(service);
        try {
            Configuration cfg = Configuration.newBuilder()
                    .setType(PayloadType.PAYLOAD_TYPE_BUILTIN)
                    .setName(safeName)
                    .setPayload(Objects.requireNonNullElse(configData, ""))
                    .build();

            Secret secret = Secret.newBuilder()
                    .setType(PayloadType.PAYLOAD_TYPE_BUILTIN)
                    .setName(safeName)
                    .setPayload(Objects.requireNonNullElse(secretsData, ""))
                    .build();

            ProvisionRequest request = ProvisionRequest.newBuilder()
                    .setConfiguration(cfg)
                    .setSecret(secret)
                    .build();

            ProvisionerGrpc.ProvisionerBlockingStub stub = ProvisionerGrpc.newBlockingStub(channel);
            ProvisionReply reply = stub.withDeadline(Deadline.after(deadlineSeconds, TimeUnit.SECONDS))
                    .provision(request);

            LOG.debugf(
                    "Successfully provisioned configuration '%s' (config URI: %s, secret URI: %s)",
                    name, reply.getConfigurationUri(), reply.getSecretUri());

            return new ProvisioningReference(
                    URI.create(reply.getConfigurationUri()),
                    URI.create(reply.getSecretUri()),
                    reply.getPropertiesMap());
        } catch (StatusRuntimeException e) {
            throw mapStatusRuntimeException(e, service);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Failed to provision configuration '%s' to service: %s", name, service.toAddress());
            throw new ServiceUnavailableException("Service is not available at the address " + service.toAddress(), e);
        } finally {
            channelManager.closeChannel(channel);
        }
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
     * @throws WanakuException if the remote service returns an error
     * @deprecated Use {@link #invokeToolAsync(ToolInvokeRequest, ServiceTarget)} instead.
     */
    @Deprecated
    @Override
    public ToolInvokeReply invokeTool(ToolInvokeRequest request, ServiceTarget service) {
        LOG.debugf("Invoking tool on service: %s", service.toAddress());

        ManagedChannel channel = createChannel(service);
        try {
            ToolInvokerGrpc.ToolInvokerBlockingStub blockingStub = ToolInvokerGrpc.newBlockingStub(channel);
            return blockingStub
                    .withDeadline(Deadline.after(deadlineSeconds, TimeUnit.SECONDS))
                    .invokeTool(request);
        } catch (StatusRuntimeException e) {
            throw mapStatusRuntimeException(e, service);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Failed to invoke tool on service: %s", service.toAddress());
            throw new ServiceUnavailableException("Service is not available at the address " + service.toAddress(), e);
        } finally {
            channelManager.closeChannel(channel);
        }
    }

    /**
     * Invokes a tool on a remote service via gRPC asynchronously.
     * <p>
     * This method creates a channel, builds a gRPC future stub, and invokes the tool
     * with the provided request. The result is returned as a {@link Uni} that completes
     * when the gRPC future resolves, without blocking any thread.
     *
     * @param request the tool invocation request
     * @param service the target service
     * @return a Uni that will emit the tool invocation reply
     * @throws ServiceUnavailableException if the service cannot be reached
     * @throws WanakuException if the remote service returns an error
     */
    @Override
    public Uni<ToolResponse> invokeToolAsync(ToolInvokeRequest request, ServiceTarget service) {
        LOG.debugf("Invoking tool asynchronously on service: %s", service.toAddress());

        final GrpcToolResponseTransformer transformer = new GrpcToolResponseTransformer();
        return Uni.createFrom()
                .<ToolInvokeReply>emitter(em -> {
                    ManagedChannel channel = createChannel(service);
                    try {
                        var future = ToolInvokerGrpc.newFutureStub(channel)
                                .withDeadline(Deadline.after(deadlineSeconds, TimeUnit.SECONDS))
                                .invokeTool(request);

                        future.addListener(
                                () -> {
                                    try {
                                        em.complete(future.get(deadlineSeconds, TimeUnit.SECONDS));
                                    } catch (Exception e) {
                                        em.fail(e);
                                    } finally {
                                        channelManager.closeChannel(channel);
                                    }
                                },
                                Infrastructure.getDefaultExecutor());
                    } catch (StatusRuntimeException e) {
                        channelManager.closeChannel(channel);
                        em.fail(mapStatusRuntimeException(e, service));
                    } catch (RuntimeException e) {
                        channelManager.closeChannel(channel);
                        LOG.errorf(e, "Failed to invoke tool on service: %s", service.toAddress());
                        em.fail(new ServiceUnavailableException(
                                "Service is not available at the address " + service.toAddress(), e));
                    }
                })
                .map(transformer::transformReply);
    }

    /**
     * Acquires a resource from a remote service via gRPC asynchronously.
     * <p>
     * This method creates a channel, builds a gRPC future stub, and acquires the resource
     * with the provided request. The result is returned as a {@link Uni} that completes
     * when the gRPC future resolves, without blocking any thread.
     *
     * @param request the resource acquisition request
     * @param service the target service
     * @return a Uni that will emit the resource reply
     * @throws ServiceUnavailableException if the service cannot be reached
     * @throws WanakuException if the remote service returns an error
     */
    @Override
    public Uni<List<ResourceContents>> acquireResourceAsync(
            ResourceRequest request,
            ServiceTarget service,
            ResourceManager.ResourceArguments arguments,
            ResourceReference mcpResource) {
        LOG.debugf("Acquiring resource asynchronously from service: %s", service.toAddress());

        final GrpcResourceResponseTransformer transformer = new GrpcResourceResponseTransformer();
        return Uni.createFrom()
                .<ResourceReply>emitter(em -> {
                    ManagedChannel channel = createChannel(service);
                    try {
                        var future = ResourceAcquirerGrpc.newFutureStub(channel)
                                .withDeadline(Deadline.after(deadlineSeconds, TimeUnit.SECONDS))
                                .resourceAcquire(request);

                        future.addListener(
                                () -> {
                                    try {
                                        em.complete(future.get(deadlineSeconds, TimeUnit.SECONDS));
                                    } catch (Exception e) {
                                        em.fail(e);
                                    } finally {
                                        channelManager.closeChannel(channel);
                                    }
                                },
                                Infrastructure.getDefaultExecutor());
                    } catch (StatusRuntimeException e) {
                        channelManager.closeChannel(channel);
                        em.fail(mapStatusRuntimeException(e, service));
                    } catch (RuntimeException e) {
                        channelManager.closeChannel(channel);
                        LOG.errorf(e, "Failed to acquire resource from service: %s", service.toAddress());
                        em.fail(new ServiceUnavailableException(
                                "Service is not available at the address " + service.toAddress(), e));
                    }
                })
                .map(reply -> transformer.transformReply(reply, arguments, mcpResource));
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
     * @throws WanakuException if the remote service returns an error
     */
    @Override
    public Iterator<CodeExecutionReply> executeCode(CodeExecutionRequest request, ServiceTarget service) {
        LOG.debugf("Executing code on service: %s", service.toAddress());

        try {
            ManagedChannel channel = createChannel(service);

            CodeExecutorGrpc.CodeExecutorBlockingStub blockingStub = CodeExecutorGrpc.newBlockingStub(channel);
            return blockingStub
                    .withDeadline(Deadline.after(deadlineSeconds * 2L, TimeUnit.SECONDS))
                    .executeCode(request);
        } catch (StatusRuntimeException e) {
            throw mapStatusRuntimeException(e, service);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Failed to execute code on service: %s", service.toAddress());
            throw new ServiceUnavailableException("Service is not available at the address " + service.toAddress(), e);
        }
    }

    /**
     * Probes the health of a remote service via gRPC.
     * <p>
     * This method creates a channel, builds a gRPC stub, and probes the service
     * health with the provided request. It handles connection errors and converts
     * them to appropriate exceptions.
     *
     * @param request the health probe request
     * @param service the target service
     * @return the health probe reply from the remote service
     * @throws ServiceUnavailableException if the service cannot be reached
     * @throws WanakuException if the remote service returns an error
     */
    @Override
    public HealthProbeReply probeHealth(HealthProbeRequest request, ServiceTarget service) {
        LOG.debugf("Probing health of service: %s", service.toAddress());

        ManagedChannel channel = createChannel(service);
        try {
            HealthProbeGrpc.HealthProbeBlockingStub blockingStub = HealthProbeGrpc.newBlockingStub(channel);
            return blockingStub
                    .withDeadline(Deadline.after(deadlineSeconds, TimeUnit.SECONDS))
                    .getStatus(request);
        } catch (StatusRuntimeException e) {
            throw mapStatusRuntimeException(e, service);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Failed to probe health of service: %s", service.toAddress());
            throw new ServiceUnavailableException("Service is not available at the address " + service.toAddress(), e);
        } finally {
            channelManager.closeChannel(channel);
        }
    }

    /**
     * Maps a gRPC StatusRuntimeException to the appropriate wanaku-specific exception.
     * <p>
     * This method examines the gRPC status code to determine the correct exception type:
     * <ul>
     *   <li>{@code UNAVAILABLE} - the service is unreachable, maps to {@link ServiceUnavailableException}</li>
     *   <li>{@code INTERNAL} - the service reported an error, maps to {@link WanakuException}
     *       with the server-side error description preserved</li>
     *   <li>All other codes - maps to {@link WanakuException} with the status description</li>
     * </ul>
     *
     * @param e the gRPC status runtime exception
     * @param service the target service that produced the error
     * @return the mapped wanaku-specific exception
     */
    private RuntimeException mapStatusRuntimeException(StatusRuntimeException e, ServiceTarget service) {
        Status status = e.getStatus();
        String description = status.getDescription();

        if (status.getCode() == Status.Code.UNAVAILABLE) {
            LOG.errorf(e, "Service unavailable: %s", service.toAddress());
            return new ServiceUnavailableException("Service is not available at the address " + service.toAddress(), e);
        }

        if (status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
            LOG.errorf(e, "Service %s did not respond within a reasonable time frame", service.getServiceName());
            return new ServiceUnavailableException(
                    String.format(
                            "Service %s did not respond within a reasonable time frame", service.getServiceName()),
                    e);
        }

        String message = description != null
                ? description
                : "gRPC error (" + status.getCode() + ") from service " + service.toAddress();
        LOG.errorf(e, "Service error from %s: %s (code: %s)", service.toAddress(), message, status.getCode());
        return new WanakuException(message, e);
    }
}
