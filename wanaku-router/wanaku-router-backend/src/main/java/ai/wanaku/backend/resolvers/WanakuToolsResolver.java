package ai.wanaku.backend.resolvers;

import java.util.Map;
import ai.wanaku.backend.bridge.ToolsBridge;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.exceptions.ToolNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;
import ai.wanaku.core.exchange.PropertySchema;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.ToolAdapter;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;

/**
 * Resolver for tools that uses a ToolsProxy to provide tool execution capabilities.
 * <p>
 * This resolver adapts the proxy's tool executor to the Tool interface using
 * the adapter pattern, maintaining separation between proxy and execution concerns.
 */
public class WanakuToolsResolver implements ToolsResolver {
    private final ToolsBridge proxy;

    public WanakuToolsResolver(ToolsBridge proxy) {
        this.proxy = proxy;
    }

    @Override
    public Tool resolve(ToolReference toolReference) {
        return new ToolAdapter(proxy.getExecutor());
    }

    @Override
    public void provision(ToolPayload toolPayload) throws ToolNotFoundException {
        // Service-side
        final ProvisioningReference provisioningReference = proxy.provision(toolPayload);

        final Map<String, PropertySchema> serviceProperties = provisioningReference.properties();

        // Client-side
        ToolReference toolReference = toolPayload.getPayload();
        final Map<String, Property> clientProperties =
                toolReference.getInputSchema().getProperties();
        for (var serviceProperty : serviceProperties.entrySet()) {
            clientProperties.computeIfAbsent(
                    serviceProperty.getKey(), v -> toProperty(serviceProperty, serviceProperties));
        }

        toolReference.setConfigurationURI(
                provisioningReference.configurationURI().toString());
        toolReference.setSecretsURI(provisioningReference.secretsURI().toString());
    }

    private static Property toProperty(
            Map.Entry<String, PropertySchema> serviceProperty, Map<String, PropertySchema> serviceProperties) {
        PropertySchema schema = serviceProperties.get(serviceProperty.getKey());
        Property property = new Property();

        property.setDescription(schema.getDescription());
        property.setType(schema.getType());

        return property;
    }
}
