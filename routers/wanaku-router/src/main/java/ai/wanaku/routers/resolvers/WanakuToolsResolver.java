package ai.wanaku.routers.resolvers;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.Property;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.exchange.PropertySchema;
import ai.wanaku.core.mcp.common.Tool;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.routers.proxies.ToolsProxy;
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
    public void loadProperties(ToolReference toolReference) throws ToolNotFoundException {
        // Service-side
        final Map<String, PropertySchema> serviceProperties = proxy.getProperties(toolReference);

        // Client-side
        final Map<String, Property> clientProperties = toolReference.getInputSchema().getProperties();
        for (var serviceProperty : serviceProperties.entrySet()) {
            clientProperties.computeIfAbsent(serviceProperty.getKey(),
                    v -> toProperty(serviceProperty, serviceProperties));
        }
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
