package org.wanaku.server.quarkus.types;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Messages {
    private static final String VERSION = "2.0";

    public static JsonObject newError(Object id, int code, String message) {
        JsonObject response = new JsonObject();
        response.put("jsonrpc", VERSION);
        response.put("id", id);
        response.put("error", new JsonObject()
                .put("code", code)
                .put("message", message));
        return response;
    }

    public static JsonObject newNotification(String method, Object params) {
        return new JsonObject()
                .put("jsonrpc", VERSION)
                .put("method", method)
                .put("params", params);
    }

    public static JsonObject newPing(Object id) {
        return new JsonObject()
                .put("jsonrpc", VERSION)
                .put("id", id)
                .put("method", "ping");
    }

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

        McpMessage message = new McpMessage();
        message.event = "message";
        message.payload = jsonRpc.toString();

        return message;
    }
}
