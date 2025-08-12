package ai.wanaku.core.forward.discovery.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

public class ForwardDiscoveryClientProvider {
    private static final Logger LOG = Logger.getLogger(ForwardDiscoveryClientProvider.class);

    @ConfigProperty(name = "wanaku.service.registration.uri", defaultValue = "http://localhost:8080")
    String registrationUri;

    @ConfigProperty(name = "wanaku.mcp.service.name")
    String name;

    @ConfigProperty(name = "wanaku.mcp.service.namespace", defaultValue = "")
    String namespace;

    @ConfigProperty(name = "wanaku.service.registration.mcp-forward-address", defaultValue = "auto")
    String forwardAddress;

    @ApplicationScoped
    @Produces
    ForwardDiscoveryClient discoveryClient() {
        if (System.getenv("WANAKU_AUTO_DISCOVERY_ENABLED") != null) {
            LOG.info("Using the auto-discovery client");
            return new AutoDiscoveryClient(registrationUri, name, namespace, forwardAddress);
        }

        LOG.info("Using NO-OP discovery client");
        return new AutoDiscoveryClient(registrationUri, name, namespace, forwardAddress);
    }
}
