package ai.wanaku.routers.proxies.resources;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.core.exchange.ResourceAcquirerGrpc;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.routers.proxies.ResourceProxy;
import com.google.protobuf.ProtocolStringList;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * A proxy class for acquiring resources
 */
public class ResourceAcquirerProxy implements ResourceProxy {
    private static final Logger LOG = Logger.getLogger(ResourceAcquirerProxy.class);

    private final ServiceRegistry serviceRegistry;

    public ResourceAcquirerProxy(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public List<ResourceContents> eval(ResourceManager.ResourceArguments arguments, ResourceReference mcpResource) {
        LOG.infof("Requesting resource on behalf of connection %s", arguments.connection().id());

        ServiceTarget service = serviceRegistry.getServiceByName(mcpResource.getType(), ServiceType.RESOURCE_PROVIDER);
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
            LOG.errorf("Unable to acquire resource for connection: %s", arguments.connection().id());

            TextResourceContents textResourceContents =
                    new TextResourceContents(arguments.requestUri().value(), reply.getContentList().get(0), "text/plain");
            return List.of(textResourceContents);
        } else {
            ProtocolStringList contentList = reply.getContentList();
            List<ResourceContents> textResourceContentsList = new ArrayList<>();

            for (String content : contentList) {
                TextResourceContents textResourceContents =
                        new TextResourceContents(arguments.requestUri().value(), content,
                                mcpResource.getMimeType());

                textResourceContentsList.add(textResourceContents);
            }

            return textResourceContentsList;
        }
    }

    @Override
    public String name() {
        return "resource-acquirer";
    }

    private ResourceReply acquireRemotely(ResourceReference mcpResource, ResourceManager.ResourceArguments arguments, ServiceTarget service) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(service.toAddress())
                .usePlaintext()
                .build();

        ResourceRequest request = ResourceRequest
                .newBuilder()
                .setLocation(mcpResource.getLocation())
                .setType(mcpResource.getType())
                .setName(mcpResource.getName())
                .setConfigurationURI(mcpResource.getConfigurationURI())
                .setSecretsURI(mcpResource.getSecretsURI())
                .build();

        ResourceAcquirerGrpc.ResourceAcquirerBlockingStub blockingStub = ResourceAcquirerGrpc.newBlockingStub(channel);
        return blockingStub.resourceAcquire(request);
    }
}
