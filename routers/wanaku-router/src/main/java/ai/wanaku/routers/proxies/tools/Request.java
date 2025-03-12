package ai.wanaku.routers.proxies.tools;

import ai.wanaku.api.types.ToolReference;
import io.quarkiverse.mcp.server.ToolManager;

record Request(String uri, String body) {

    public static Request newRequest(ToolReference toolReference, ToolManager.ToolArguments toolArguments) {
        String uri = toolReference.getUri();
        String body = null;
        for (ToolReference.Property t : toolReference.getInputSchema().getProperties()) {
            if (t.getPropertyName().equals("_body")) {
                body = toolArguments.args().get("_body").toString();
            }
        }

        if (body == null) {
            body = "";
        }

        return new Request(uri, body);
    }
}
