package ai.wanaku.core.exchange;


public record ParsedToolInvokeRequest(String uri, String body) {

    public static ParsedToolInvokeRequest parseRequest(ToolInvokeRequest toolInvokeRequest) {
        String uri = toolInvokeRequest.getUri();
        String body = null;
        for (var t : toolInvokeRequest.getArgumentsMap().entrySet()) {
            if (!t.getKey().equals("_body")) {
                Object o = toolInvokeRequest.getArgumentsMap().get(t.getKey());
                uri = uri.replace(String.format("{%s}", t.getKey()), o.toString());
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
