package ai.wanaku.routers.proxies.tools;

import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.types.CallableReference;
import ai.wanaku.api.types.Property;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.io.ToolPayload;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.core.exchange.PayloadType;
import ai.wanaku.core.exchange.ProvisionReply;
import ai.wanaku.core.exchange.ProvisionRequest;
import ai.wanaku.core.exchange.ProvisionerGrpc;
import ai.wanaku.core.exchange.PropertySchema;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokerGrpc;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.util.CollectionsHelper;
import ai.wanaku.core.exchange.Configuration;
import ai.wanaku.core.exchange.Secret;
import ai.wanaku.routers.proxies.ToolsProxy;
import ai.wanaku.routers.support.ProvisioningReference;
import com.google.protobuf.ProtocolStringList;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

import static ai.wanaku.core.util.ReservedArgumentNames.BODY;
import static ai.wanaku.core.util.ReservedPropertyNames.SCOPE_SERVICE;
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
    public ToolResponse call(ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
        LOG.infof("Calling tool on behalf of connection %s", toolArguments.connection().id());
        if (toolReference instanceof ToolReference ref) {
            return call(toolArguments, ref);
        }

        LOG.errorf("Tool reference %s not supported", toolReference == null ? "null" : toolReference.getClass().getName());
        throw new UnsupportedOperationException("Only local tool call references should be invoked by this proxy");
    }

    private ToolResponse call(ToolManager.ToolArguments toolArguments, ToolReference toolReference) {
        ServiceTarget service = serviceRegistry.getServiceByName(toolReference.getType(), ServiceType.TOOL_INVOKER);
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
            LOG.errorf(e, "Unable to call endpoint: %s (connection: %s)", e.getMessage(), toolArguments.connection().id());
            return ToolResponse.error(e.getMessage());
        }
    }

    private static ToolInvokeReply invokeRemotely(
            ToolReference toolReference, ToolManager.ToolArguments toolArguments, ServiceTarget service) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(service.toAddress()).usePlaintext().build();

        Map<String, String> argumentsMap = CollectionsHelper.toStringStringMap(toolArguments.args());

        Map<String, Property> inputSchema = toolReference
                .getInputSchema()
                .getProperties();

        // extract headers parameter
        Map<String, String> headers = inputSchema.entrySet()
                .stream()
                .filter(entry -> {
                    Property property = entry.getValue();
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
                .setConfigurationURI(toolReference.getConfigurationURI())
                .setSecretsURI(toolReference.getSecretsURI())
                .putAllHeaders(headers)
                .putAllArguments(argumentsMap)
                .build();

        ToolInvokerGrpc.ToolInvokerBlockingStub blockingStub = ToolInvokerGrpc.newBlockingStub(channel);
        return blockingStub.invokeTool(toolInvokeRequest);
    }

    private static String extractBody(ToolReference toolReference, ToolManager.ToolArguments toolArguments) {
        // First, check if the tool specification defines it as having a body
        Map<String, Property> properties = toolReference.getInputSchema().getProperties();
        Property bodyProp = properties.get(BODY);
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
    public ProvisioningReference provision(ToolPayload toolPayload) {
        ToolReference toolReference = toolPayload.getToolReference();

        ServiceTarget service = serviceRegistry.getServiceByName(toolReference.getType(), ServiceType.TOOL_INVOKER);
        if (service == null) {
            throw new ServiceNotFoundException("There is no host registered for service " + toolReference.getType());
        }

        ManagedChannel channel = ManagedChannelBuilder.forTarget(service.toAddress())
                .usePlaintext()
                .build();



        final Configuration cfg = Configuration.newBuilder()
                .setType(PayloadType.BUILTIN)
                .setName(toolReference.getName())
                .setPayload(Objects.requireNonNullElse(toolPayload.getConfigurationData(), ""))
                .build();

        final Secret secret = Secret.newBuilder()
                .setType(PayloadType.BUILTIN)
                .setName(toolReference.getName())
                .setPayload(Objects.requireNonNullElse(toolPayload.getSecretsData(), ""))
                .build();

        ProvisionRequest inquireRequest = ProvisionRequest.newBuilder()
                .setConfiguration(cfg)
                .setSecret(secret)
                .build();
        ProvisionerGrpc.ProvisionerBlockingStub blockingStub = ProvisionerGrpc.newBlockingStub(channel);
        ProvisionReply inquire = blockingStub.provision(inquireRequest);
        final String configurationUri = inquire.getConfigurationUri();
        final String secretUri = inquire.getSecretUri();
        final Map<String, PropertySchema> propertiesMap = inquire.getPropertiesMap();

        return new ProvisioningReference(URI.create(configurationUri), URI.create(secretUri), propertiesMap);
    }

    @Override
    public String name() {
        return "invoker";
    }
}
