package ai.wanaku.routers.proxies.tools;

import io.quarkiverse.mcp.server.ToolManager;
import ai.wanaku.api.types.ToolReference;

record Request(String uri, String body) {

    public static Request newRequest(ToolReference toolReference, ToolManager.ToolArguments toolArguments) {
        String uri = toolReference.getUri();
        String body = null;
        for (var t : toolReference.getInputSchema().getProperties().entrySet()) {
            if (t.getKey().equals("_body")) {
                body = toolArguments.args().get("_body").toString();
            }
        }

        if (body == null) {
            body = "";
        }

        return new Request(uri, body);
    }
}
