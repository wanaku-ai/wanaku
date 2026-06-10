package ai.wanaku.core.capabilities.common;

import org.jboss.logging.MDC;

public interface RequestContext {
    String MDC_REQUEST_ID_KEY = "requestId";
    String MDC_CONNECTION_ID_KEY = "connectionId";
    String SPAN_ATTR_REQUEST_ID = "wanaku.mcp.request_id";
    String SPAN_ATTR_CONNECTION_ID = "wanaku.mcp.connection_id";
    String SPAN_ATTR_TOOL_NAME = "wanaku.mcp.tool_name";
    String SPAN_ATTR_RESOURCE_NAME = "wanaku.mcp.resource_name";

    void setRequestId(String requestId);

    void setConnectionId(String connectionId);

    void setToolName(String toolName);

    void setResourceName(String resourceName);

    void clear();

    static void withRequestId(String requestId, Runnable action) {
        if (requestId != null && !requestId.isEmpty()) {
            MDC.put(MDC_REQUEST_ID_KEY, requestId);
        }
        try {
            action.run();
        } finally {
            if (requestId != null && !requestId.isEmpty()) {
                MDC.remove(MDC_REQUEST_ID_KEY);
            }
        }
    }
}
