package org.wanaku.server.quarkus.helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.jboss.logging.Logger;
import org.wanaku.api.types.McpMessage;
import org.wanaku.api.types.McpRequestStatus;
import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;
import org.wanaku.api.types.McpTool;
import org.wanaku.api.types.McpToolContent;
import org.wanaku.api.types.McpToolStatus;

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

        JsonObject resources = new JsonObject();
        resources.put("subscribe", false);
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

    public static McpMessage newForResourceRead(JsonObject request, List<McpResourceData> resourcesList, String nextCursor) {
        return newForResourceRead(request.getInteger("id"), resourcesList, nextCursor);
    }

    public static McpMessage newForResourceRead(int id, List<McpResourceData> resourcesList, String nextCursor) {
        JsonObject jsonRpc = new JsonObject();
        jsonRpc.put("jsonrpc", VERSION);
        jsonRpc.put("id", id);

        JsonObject result = new JsonObject();

        JsonArray contents = new JsonArray();
        for (McpResourceData mcpResource : resourcesList) {
            JsonObject resource = new JsonObject();
            resource.put("uri", mcpResource.uri)
                    .put("mimeType", mcpResource.mimeType)
                    .put("text", mcpResource.text);
            contents.add(resource);
        }

        result.put("contents", contents);
        result.put("nextCursor", nextCursor);

        jsonRpc.put("result", result);

        return toMessage(jsonRpc);
    }

    public static McpMessage newError(JsonObject request, McpRequestStatus.Status status) {
        return newError(request.getInteger("id"), status);
    }

    public static McpMessage newError(int id, McpRequestStatus.Status status) {
        JsonObject response = new JsonObject();
        response.put("jsonrpc", VERSION);
        response.put("id", id);
        response.put("error", new JsonObject()
                .put("code", status.getCode())
                .put("message", status.getDescription()));
        return toMessage(response);
    }

    public static McpMessage newNotification(String method, HashMap<String, String> paramsMap) {
        JsonObject jsonObject = new JsonObject()
                .put("jsonrpc", VERSION)
                .put("method", method);


        JsonArray params = new JsonArray();

        if (paramsMap != null) {
            for (Map.Entry<String, String> entry : paramsMap.entrySet()) {
                JsonObject param = new JsonObject();
                param.put(entry.getKey(), entry.getValue());

                params.add(param);
            }
        }

        return toMessage(jsonObject);
    }

    public static McpMessage newForToolsList(JsonObject request, List<McpTool> resourcesList, String nextCursor) {
        return newForToolsList(request.getInteger("id"), resourcesList, nextCursor);
    }

    public static McpMessage newForToolsList(int id, List<McpTool> toolList, String nextCursor) {
        JsonObject jsonRpc = new JsonObject();
        jsonRpc.put("jsonrpc", VERSION);
        jsonRpc.put("id", id);

        JsonObject result = new JsonObject();

        JsonArray tools = new JsonArray();
        for (McpTool mcpTool : toolList) {
            JsonObject properties = new JsonObject();
            List<String> required = new ArrayList<>();
            for (var property : mcpTool.inputSchema.properties) {
                JsonObject propertyJson = new JsonObject();
                propertyJson.put("type", property.description);
                propertyJson.put("description", property.description);

                properties.put(property.name, property);
                if (property.required) {
                    required.add(property.name);
                }
            }

            JsonObject inputSchema = new JsonObject()
                    .put("type", mcpTool.inputSchema.type)
                    .put("properties", properties)
                    .put("required", required);

            JsonObject tool = new JsonObject();
            tool.put("name", mcpTool.name);
            tool.put("description", mcpTool.description);
            tool.put("inputSchema", inputSchema);
            tools.add(tool);
        }

        result.put("tools", tools);
        result.put("nextCursor", nextCursor);

        jsonRpc.put("result", result);

        return toMessage(jsonRpc);
    }


    public static McpMessage newForToolsCall(JsonObject request, McpToolStatus status) {
        return newForToolsLCall(request.getInteger("id"), status);
    }

    public static McpMessage newForToolsLCall(int id, McpToolStatus status) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("jsonrpc", VERSION);
        jsonObject.put("id", id);

        JsonObject result = new JsonObject();

        JsonArray contents = new JsonArray();
        for (McpToolContent mcpToolContent : status.content) {
            JsonObject content = new JsonObject()
                    .put("type", mcpToolContent.type)
                    .put("text", mcpToolContent.text);

            contents.add(content);
        }

        result.put("contents", contents);
        result.put("isError", status.isError);

        jsonObject.put("result", result);

        return toMessage(jsonObject);
    }
}
