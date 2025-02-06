package org.wanaku.server.quarkus;

import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import io.smallrye.mutiny.Multi;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;
import org.wanaku.api.types.McpMessage;
import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.api.types.McpRequestStatus;
import org.wanaku.api.types.McpResourceData;
import org.wanaku.server.quarkus.helper.Messages;

@Dependent
public class McpController {
    private static final Logger LOG = Logger.getLogger(McpController.class);

    @ConfigProperty(name = "quarkus.http.host")
    String host;

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @Inject
    ResourceResolver resourceResolver;

    @Inject
    @Channel("mcpEvents")
    MutinyEmitter<McpMessage> mcpEvents;

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
                response = Messages.newForResourceList(request, resourceResolver.resources(), Pagination.nextPage());
                break;
            }
            case "resources/read": {
                String uri = request.getJsonObject("params").getString("uri");
                response = Messages.newForResourceRead(request, resourceResolver.read(uri), Pagination.nextPage());
                break;
            }
            case "resources/subscribe": {
                String uri = request.getJsonObject("params").getString("uri");
                resourceResolver.subscribe(uri, status -> onUpdate(request, status));
                return Multi.createFrom().empty();
            }
            default: {
                response = null;
                break;
            }
        }

        LOG.debugf("Replying with %s", response.payload);

        return Multi.createFrom().item(response);
    }

    private void onUpdate(JsonObject request, McpRequestStatus<McpResourceData> subscriptionStatus) {
        McpMessage response;
        if (subscriptionStatus.status == McpRequestStatus.Status.SUCCESS) {
            response = Messages.newNotification("notifications/resources/updated", null);
        } else {
            response = Messages.newError(request, subscriptionStatus.status);
        }

        mcpEvents.sendAndForget(response);
    }
}
