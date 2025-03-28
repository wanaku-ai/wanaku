package ai.wanaku.routers.proxies.tools;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Configurations;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.exchange.InquireReply;
import ai.wanaku.core.exchange.InquireRequest;
import ai.wanaku.core.exchange.InquirerGrpc;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokerGrpc;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.util.CollectionsHelper;
import ai.wanaku.routers.proxies.ToolsProxy;
import com.google.protobuf.ProtocolStringList;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import static ai.wanaku.core.util.ReservedArgumentNames.BODY;
import static ai.wanaku.core.util.ReservedPropertyNames.SCOPE_SERVICE;
import static ai.wanaku.core.util.ReservedPropertyNames.SCOPE_SERVICE_ENDPOINT;
import static ai.wanaku.core.util.ReservedPropertyNames.TARGET_CONFIGURATION;
import static ai.wanaku.core.util.ReservedPropertyNames.TARGET_HEADER;

/**
 * A proxy class for invoking tools
 */
public class InvokerProxy implements ToolsProxy {
    private static final Logger LOG = Logger.getLogger(InvokerProxy.class);
    private static final String EMPTY_BODY = "";

    private final ServiceRegistry serviceRegistry;

    public InvokerProxy(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public ToolResponse call(ToolReference toolReference, ToolManager.ToolArguments toolArguments) {
        Service service = serviceRegistry.getService(toolReference.getType());
        if (service == null) {
            return ToolResponse.error("There is no host registered for service " + toolReference.getType());
        }

        LOG.infof("Invoking %s on %s", toolReference.getType(), service);
        try {
            final ToolInvokeReply invokeReply = invokeRemotely(toolReference, toolArguments, service);

            if (invokeReply.getIsError()) {
                return ToolResponse.error(invokeReply.getContentList().get(0));
            } else {
                ProtocolStringList contentList = invokeReply.getContentList();
                List<TextContent> contents = new ArrayList<>(invokeReply.getContentList().size());
                contentList.stream().map(TextContent::new).forEach(contents::add);

                return ToolResponse.success(contents);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Unable to call endpoint: %s", e.getMessage());
            return ToolResponse.error(e.getMessage());
        }
    }

    private static ToolInvokeReply invokeRemotely(
            ToolReference toolReference, ToolManager.ToolArguments toolArguments, Service service) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(service.getTarget()).usePlaintext().build();

        Map<String, Configuration> configurations = service.getConfigurations().getConfigurations();
        Map<String, String> serviceConfigurations = Configurations.toStringMap(configurations);
        Map<String, String> argumentsMap = CollectionsHelper.toStringStringMap(toolArguments.args());

        Map<String, ToolReference.Property> inputSchema =toolReference
                .getInputSchema()
                .getProperties();

        Map<String,String> serviceEndpointConfiguration = inputSchema.entrySet()
                .stream()
                .filter(entry -> {
                    ToolReference.Property property = entry.getValue();
                    return property !=null &&
                            property.getTarget() != null &&
                            property.getScope() != null &&
                            property.getTarget().equals(TARGET_CONFIGURATION) &&
                            property.getScope().equals(SCOPE_SERVICE_ENDPOINT);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));

        //override the global configuration with the invocation specific one.
        serviceConfigurations.putAll(serviceEndpointConfiguration);


        // extract headers parameter
        Map<String, String> headers = inputSchema.entrySet()
                .stream()
                .filter(entry -> {
                    ToolReference.Property property = entry.getValue();
                    return  property !=null &&
                            property.getTarget() != null &&
                            property.getScope() != null &&
                            property.getTarget().equals(TARGET_HEADER) &&
                            property.getScope().equals(SCOPE_SERVICE);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));


        String body = extractBody(toolReference, toolArguments);

        ToolInvokeRequest toolInvokeRequest = ToolInvokeRequest.newBuilder()
                .setBody(body)
                .setUri(toolReference.getUri())
                .putAllServiceConfigurations(serviceConfigurations)
                .putAllHeaders(headers)
                .putAllArguments(argumentsMap)
                .build();

        ToolInvokerGrpc.ToolInvokerBlockingStub blockingStub = ToolInvokerGrpc.newBlockingStub(channel);
        return blockingStub.invokeTool(toolInvokeRequest);
    }

    private static String extractBody(ToolReference toolReference, ToolManager.ToolArguments toolArguments) {
        // First, check if the tool specification defines it as having a body
        Map<String, ToolReference.Property> properties = toolReference.getInputSchema().getProperties();
        ToolReference.Property bodyProp = properties.get(BODY);
        if (bodyProp == null) {
            // If the tool does not specify a body, then return an empty string
            return EMPTY_BODY;
        }

        // If there is a body defined, then get it from the arguments from the LLM
        String body = (String) toolArguments.args().get(BODY);
        if (body == null) {
            // If the LLM does not provide a body, then return an empty string
            return EMPTY_BODY;
        }

        // Use the body provided by the LLM
        return body;
    }

    @Override
    public Map<String, String> getServiceConfigurations(String target) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();

        InquireRequest inquireRequest = InquireRequest.newBuilder().build();
        InquirerGrpc.InquirerBlockingStub blockingStub = InquirerGrpc.newBlockingStub(channel);
        InquireReply inquire = blockingStub.inquire(inquireRequest);
        return inquire.getServiceConfigurationsMap();
    }

    @Override
    public String name() {
        return "invoker";
    }
}
