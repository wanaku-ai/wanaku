package org.wanaku.server.quarkus;

import java.util.List;
import java.util.Map;

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
import org.wanaku.api.exceptions.ToolNotFoundException;
import org.wanaku.api.resolvers.ToolsResolver;
import org.wanaku.api.types.McpMessage;
import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.api.types.McpRequestStatus;
import org.wanaku.api.types.McpResourceData;
import org.wanaku.api.types.McpTool;
import org.wanaku.api.types.McpToolStatus;
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
    ToolsResolver toolsResolver;

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
                response = Messages.newForResourceList(request, resourceResolver.list(), Pagination.nextPage());
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
            case "tools/list": {
                response = Messages.newForToolsList(request, toolsResolver.list(), Pagination.nextPage());
                break;
            }
            case "tools/call": {
                // 1. Retrieve tool name from request
                String toolName = request.getJsonObject("params").getString("name");
                // 2. Find tool by name
                McpTool mcpTool;
                try {
                    mcpTool = toolsResolver.find(toolName);

                    // 3. Retrieve arguments from request and build a map
                    Map<String, Object> arguments = request.getJsonObject("params").getJsonObject("arguments").getMap();

                    // 4. Invoke the tool
                    McpToolStatus toolStatus = toolsResolver.call(mcpTool, arguments);
                    response = Messages.newForToolsCall(request, toolStatus);
                    break;
                } catch (ToolNotFoundException e) {
                    LOG.errorf("Tool %s not found", toolName);
                    response = Messages.newError(request, McpRequestStatus.Status.INTERNAL_ERROR);
                    break;
                }


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
