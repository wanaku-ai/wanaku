package org.wanaku.server.quarkus.helper;

import java.util.List;
import java.util.UUID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.jboss.logging.Logger;
import org.wanaku.server.quarkus.types.McpMessage;
import org.wanaku.server.quarkus.types.McpResource;

public class Messages {
    private static final Logger LOG = Logger.getLogger(Messages.class);
    private static final String VERSION = "2.0";

    public static McpMessage newForInitialization(JsonObject request) {
        return newForInitialization(request.getInteger("id"));
    }

    private static McpMessage newForInitialization(int id) {
        JsonObject jsonRpc = new JsonObject();
        jsonRpc.put("jsonrpc", VERSION);
        jsonRpc.put("id", id);

        JsonObject result = new JsonObject();
        result.put("protocolVersion", "2024-11-05");

        JsonObject capabilities = new JsonObject();
        JsonObject logging = new JsonObject();
        capabilities.put("logging", logging);

        JsonObject prompts = new JsonObject();
        prompts.put("listChanged", true);
        capabilities.put("prompts", prompts);

        JsonObject resources = new JsonObject();
        resources.put("subscribe", true);
        resources.put("listChanged", true);
        capabilities.put("resources", resources);

        JsonObject tools = new JsonObject();
        tools.put("listChanged", true);
        capabilities.put("tools", tools);

        result.put("capabilities", capabilities);

        JsonObject serverInfo = new JsonObject();
        serverInfo.put("name", "Wanaku");
        serverInfo.put("version", "1.0.0");
        result.put("serverInfo", serverInfo);

        jsonRpc.put("result", result);

        return toMessage(jsonRpc);
    }

    public static McpMessage newForResourceList(JsonObject request, List<McpResource> resourcesList, String nextCursor) {
        return newForResourceList(request.getInteger("id"), resourcesList, nextCursor);
    }

    public static McpMessage newForResourceList(int id, List<McpResource> resourcesList, String nextCursor) {
        JsonObject jsonRpc = new JsonObject();
        jsonRpc.put("jsonrpc", VERSION);
        jsonRpc.put("id", id);

        JsonObject result = new JsonObject();

        JsonArray resources = new JsonArray();
        for (McpResource mcpResource : resourcesList) { // You can change this to generate multiple resources
            JsonObject resource = new JsonObject();
            resource.put("uri", mcpResource.uri)
                    .put("name", mcpResource.name)
                    .put("description", mcpResource.description)
                    .put("mimeType", mcpResource.mimeType);
            resources.add(resource);
        }

        result.put("resources", resources);
        result.put("nextCursor", nextCursor);

        jsonRpc.put("result", result);

        return toMessage(jsonRpc);
    }

    private static McpMessage toMessage(JsonObject jsonRpc) {
        McpMessage message = new McpMessage();
        message.event = "message";
        message.payload = jsonRpc.toString();
        return message;
    }

    public static McpMessage newConnectionMessage(String host, int port) {
        McpMessage message = new McpMessage();

        message.event = "endpoint";
        message.payload = endpoint(host, port);
        return message;
    }

    public static String endpoint(String host, int port) {
        String uuid = UUID.randomUUID().toString();
        LOG.infof("Created new session %s", uuid);

        return endpoint(host, port, uuid);
    }

    public static String endpoint(String host, int port, String uuid) {
        return String.format("%s/message?sessionId=%s", baseAddress(host, port), uuid);
    }

    private static String baseAddress(String host, int port) {
        return String.format("http://%s:%d", host, port);
    }
}
