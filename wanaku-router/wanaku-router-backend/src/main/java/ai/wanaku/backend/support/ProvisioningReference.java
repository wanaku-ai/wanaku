package ai.wanaku.backend.support;

import java.net.URI;
import java.util.Map;
import ai.wanaku.core.exchange.PropertySchema;

public record ProvisioningReference(URI configurationURI, URI secretsURI, Map<String, PropertySchema> properties) {}
