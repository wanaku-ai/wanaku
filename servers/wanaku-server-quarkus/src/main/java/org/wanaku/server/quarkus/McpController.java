package org.wanaku.server.quarkus;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import io.smallrye.mutiny.Multi;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;
import org.wanaku.server.quarkus.api.ResourceResolver;
import org.wanaku.server.quarkus.helper.Messages;
import org.wanaku.server.quarkus.types.McpMessage;

@Dependent
public class McpController {
    private static final Logger LOG = Logger.getLogger(McpController.class);

    @ConfigProperty(name = "quarkus.http.host")
    String host;

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @Inject
    ResourceResolver resourceResolver;

    @PostConstruct
    void initChannel() {
        LOG.info("McpController instantiated");
    }

    @Incoming("mcpNewConnections")
    @Outgoing("mcpEvents")
    public McpMessage handle(String request) {
        return Messages.newConnectionMessage(host, port);
    }

    @Incoming("mcpRequests")
    @Outgoing("mcpEvents")
    public Multi<McpMessage> requests(String str) {
        LOG.debugf("Received %s", str);
        JsonObject request = new JsonObject(str);
        McpMessage response;

        String method = request.getString("method");
        switch (method) {
            case "initialize": {
                response = Messages.newForInitialization(request);
                break;
            }
            case "notifications/initialized": {
                return Multi.createFrom().empty();
            }
            case "resources/list": {
                response = Messages.newForResourceList(request, resourceResolver.resources(), "abc");
                break;
            }
            case "resources/read": {
                String uri = request.getJsonObject("params").getString("uri");
                response = Messages.newForResourceRead(request, resourceResolver.read(uri), "abc");
                break;
            }
            default: {
                response = null;
                break;
            }
        }

        LOG.debugf("Replying with %s", response.payload);

        return Multi.createFrom().item(response);
    }
}
