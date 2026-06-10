package ai.wanaku.backend.bridge;

import org.jboss.logging.MDC;
import io.opentelemetry.api.trace.Span;

final class RequestIdContext {

    static final String MDC_REQUEST_ID_KEY = "requestId";
    static final String MDC_CONNECTION_ID_KEY = "connectionId";

    static final String SPAN_ATTR_REQUEST_ID = "wanaku.mcp.request_id";
    static final String SPAN_ATTR_CONNECTION_ID = "wanaku.mcp.connection_id";
    static final String SPAN_ATTR_TOOL_NAME = "wanaku.mcp.tool_name";
    static final String SPAN_ATTR_RESOURCE_NAME = "wanaku.mcp.resource_name";

    private RequestIdContext() {}

    static void setContext(String requestId, String connectionId) {
        if (requestId != null && !requestId.isEmpty()) {
            MDC.put(MDC_REQUEST_ID_KEY, requestId);
            Span.current().setAttribute(SPAN_ATTR_REQUEST_ID, requestId);
        }
        if (connectionId != null && !connectionId.isEmpty()) {
            MDC.put(MDC_CONNECTION_ID_KEY, connectionId);
            Span.current().setAttribute(SPAN_ATTR_CONNECTION_ID, connectionId);
        }
    }

    static void setToolName(String toolName) {
        if (toolName != null && !toolName.isEmpty()) {
            Span.current().setAttribute(SPAN_ATTR_TOOL_NAME, toolName);
        }
    }

    static void setResourceName(String resourceName) {
        if (resourceName != null && !resourceName.isEmpty()) {
            Span.current().setAttribute(SPAN_ATTR_RESOURCE_NAME, resourceName);
        }
    }

    static void clear() {
        MDC.remove(MDC_REQUEST_ID_KEY);
        MDC.remove(MDC_CONNECTION_ID_KEY);
    }
}
