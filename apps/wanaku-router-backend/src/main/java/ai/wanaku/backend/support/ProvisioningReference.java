package ai.wanaku.backend.support;

import java.net.URI;
import java.util.Map;
import ai.wanaku.core.exchange.v1.PropertySchema;

public record ProvisioningReference(URI configurationURI, URI secretsURI, Map<String, PropertySchema> properties) {}
