package ai.wanaku.backend.resolvers;

import ai.wanaku.backend.proxies.ToolsProxy;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.exceptions.ToolNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.io.ToolPayload;
import ai.wanaku.core.exchange.PropertySchema;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import java.util.Map;

public class WanakuToolsResolver implements ToolsResolver {
    private final ToolsProxy proxy;

    public WanakuToolsResolver(ToolsProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public Tool resolve(ToolReference toolReference) {
        return proxy;
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
