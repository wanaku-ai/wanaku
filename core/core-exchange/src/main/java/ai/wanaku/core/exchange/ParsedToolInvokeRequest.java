package ai.wanaku.core.exchange;


import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record ParsedToolInvokeRequest(String uri, String body) {

    public static ParsedToolInvokeRequest parseRequest(ToolInvokeRequest toolInvokeRequest) {
        String uri = toolInvokeRequest.getUri();
        String body = null;
        for (var t : toolInvokeRequest.getArgumentsMap().entrySet()) {
            if (!t.getKey().equals("_body")) {
                Object o = toolInvokeRequest.getArgumentsMap().get(t.getKey());
                String encoded = URLEncoder.encode(o.toString(), StandardCharsets.UTF_8);
                uri = uri.replace(String.format("{%s}", t.getKey()), encoded);
            } else {
                body = toolInvokeRequest.getArgumentsMap().get("_body").toString();
            }
        }

        if (body == null) {
            body = "";
        }

        return new ParsedToolInvokeRequest(uri, body);
    }
}
