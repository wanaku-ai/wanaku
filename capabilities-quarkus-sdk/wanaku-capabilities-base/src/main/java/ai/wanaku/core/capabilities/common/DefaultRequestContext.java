package ai.wanaku.core.capabilities.common;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.MDC;
import io.opentelemetry.api.trace.Span;

@ApplicationScoped
public class DefaultRequestContext implements RequestContext {

    @Override
    public void setRequestId(String requestId) {
        if (requestId != null && !requestId.isEmpty()) {
            MDC.put(MDC_REQUEST_ID_KEY, requestId);
            Span.current().setAttribute(SPAN_ATTR_REQUEST_ID, requestId);
        }
    }

    @Override
    public void setConnectionId(String connectionId) {
        if (connectionId != null && !connectionId.isEmpty()) {
            MDC.put(MDC_CONNECTION_ID_KEY, connectionId);
            Span.current().setAttribute(SPAN_ATTR_CONNECTION_ID, connectionId);
        }
    }

    @Override
    public void setToolName(String toolName) {
        if (toolName != null && !toolName.isEmpty()) {
            Span.current().setAttribute(SPAN_ATTR_TOOL_NAME, toolName);
        }
    }

    @Override
    public void setResourceName(String resourceName) {
        if (resourceName != null && !resourceName.isEmpty()) {
            Span.current().setAttribute(SPAN_ATTR_RESOURCE_NAME, resourceName);
        }
    }

    @Override
    public void clear() {
        MDC.remove(MDC_REQUEST_ID_KEY);
        MDC.remove(MDC_CONNECTION_ID_KEY);
    }
}
