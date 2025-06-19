package ai.wanaku.routers.support;

import ai.wanaku.core.exchange.PropertySchema;
import java.net.URI;
import java.util.Map;

public record ProvisioningReference(URI configurationURI, URI secretsURI, Map<String, PropertySchema> properties) {
}
