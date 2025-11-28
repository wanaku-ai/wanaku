package ai.wanaku.backend.proxies;

import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.io.ResourcePayload;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.exchange.Configuration;
import ai.wanaku.core.exchange.PayloadType;
import ai.wanaku.core.exchange.ResourceAcquirerGrpc;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.exchange.Secret;
import com.google.protobuf.ProtocolStringList;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jboss.logging.Logger;

/**
 * A proxy class for acquiring resources
 */
public class ResourceAcquirerProxy implements ResourceProxy {
    private static final Logger LOG = Logger.getLogger(ResourceAcquirerProxy.class);
    private static final String EMPTY_ARGUMENT = "";
    private final ServiceResolver serviceResolver;

    public ResourceAcquirerProxy(ServiceResolver serviceResolver) {
        this.serviceResolver = serviceResolver;
    }

    @Override
    public List<ResourceContents> eval(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        LOG.infof(
                "Requesting resource on behalf of connection %s",
                arguments.connection().id());

        ServiceTarget service = serviceResolver.resolve(mcpResource.getType(), ServiceType.RESOURCE_PROVIDER);
        if (service == null) {
            String message = String.format("There is no service registered for service %s", mcpResource.getType());
            LOG.error(message);

            TextResourceContents textResourceContents =
                    new TextResourceContents(arguments.requestUri().value(), message, "text/plain");
            return List.of(textResourceContents);
        }

        LOG.infof("Requesting %s from %s", mcpResource.getName(), service.toAddress());
        final ResourceReply reply = acquireRemotely(mcpResource, arguments, service);
        if (reply.getIsError()) {
            LOG.errorf(
                    "Unable to acquire resource for connection: %s",
                    arguments.connection().id());

            TextResourceContents textResourceContents = new TextResourceContents(
                    arguments.requestUri().value(), reply.getContentList().get(0), "text/plain");
            return List.of(textResourceContents);
        } else {
            ProtocolStringList contentList = reply.getContentList();
            List<ResourceContents> textResourceContentsList = new ArrayList<>();

            for (String content : contentList) {
                TextResourceContents textResourceContents =
                        new TextResourceContents(arguments.requestUri().value(), content, mcpResource.getMimeType());

                textResourceContentsList.add(textResourceContents);
            }

            return textResourceContentsList;
        }
    }

    @Override
    public String name() {
        return "resource-acquirer";
    }

    private ResourceReply acquireRemotely(
            ResourceReference mcpResource, ResourceManager.ResourceArguments arguments, ServiceTarget service) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(service.toAddress())
                .usePlaintext()
                .build();

        ResourceRequest request = ResourceRequest.newBuilder()
                .setLocation(mcpResource.getLocation())
                .setType(mcpResource.getType())
                .setName(mcpResource.getName())
                .setConfigurationURI(Objects.requireNonNullElse(mcpResource.getConfigurationURI(), EMPTY_ARGUMENT))
                .setSecretsURI(Objects.requireNonNullElse(mcpResource.getSecretsURI(), EMPTY_ARGUMENT))
                .build();

        try {
            ResourceAcquirerGrpc.ResourceAcquirerBlockingStub blockingStub =
                    ResourceAcquirerGrpc.newBlockingStub(channel);
            return blockingStub.resourceAcquire(request);
        } catch (Exception e) {
            throw ServiceUnavailableException.forAddress(service.toAddress());
        }
    }

    @Override
    public ProvisioningReference provision(ResourcePayload payload) {
        ResourceReference resourceReference = payload.getPayload();

        ServiceTarget service = serviceResolver.resolve(resourceReference.getType(), ServiceType.RESOURCE_PROVIDER);
        if (service == null) {
            throw new ServiceNotFoundException(
                    "There is no host registered for service " + resourceReference.getType());
        }

        ManagedChannel channel = ManagedChannelBuilder.forTarget(service.toAddress())
                .usePlaintext()
                .build();

        final String configData = Objects.requireNonNullElse(payload.getConfigurationData(), "");
        final Configuration cfg = Configuration.newBuilder()
                .setType(PayloadType.BUILTIN)
                .setName(resourceReference.getName())
                .setPayload(configData)
                .build();

        final String secretsData = Objects.requireNonNullElse(payload.getSecretsData(), "");
        final Secret secret = Secret.newBuilder()
                .setType(PayloadType.BUILTIN)
                .setName(resourceReference.getName())
                .setPayload(secretsData)
                .build();

        return ProxyHelper.provision(cfg, secret, channel, service);
    }
}
