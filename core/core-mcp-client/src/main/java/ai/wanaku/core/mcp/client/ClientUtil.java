package ai.wanaku.core.mcp.client;

import java.util.HashMap;
import java.util.Map;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;

public class ClientUtil {

    public static McpClient createClient(String address) {
        return createClient(address, null);
    }

    public static McpClient createClient(String address, McpHeadersSupplier headersSupplier) {
        McpTransport transport;
        McpHeadersSupplier combinedHeaders = combineHeaders(headersSupplier);

        if (address.endsWith("sse") || address.endsWith("sse/")) {
            HttpMcpTransport.Builder builder = new HttpMcpTransport.Builder()
                    .sseUrl(address)
                    .logRequests(true)
                    .logResponses(true)
                    .customHeaders(combinedHeaders);
            transport = builder.build();
        } else {
            StreamableHttpMcpTransport.Builder builder = new StreamableHttpMcpTransport.Builder()
                    .url(address)
                    .logRequests(true)
                    .logResponses(true)
                    .customHeaders(combinedHeaders);
            transport = builder.build();
        }

        return new DefaultMcpClient.Builder().transport(transport).build();
    }

    private static McpHeadersSupplier combineHeaders(McpHeadersSupplier extraHeaders) {
        return callContext -> {
            Map<String, String> headers = new HashMap<>();

            // Propagate W3C trace context
            Span currentSpan = Span.current();
            if (currentSpan.getSpanContext().isValid()) {
                W3CTraceContextPropagator propagator = W3CTraceContextPropagator.getInstance();
                propagator.inject(Context.current(), headers, Map::put);
            }

            // Propagate MCP request ID from MDC
            String requestId = (String) org.jboss.logging.MDC.get("requestId");
            if (requestId != null && !requestId.isEmpty()) {
                headers.put("x-wanaku-request-id", requestId);
            }

            if (extraHeaders != null) {
                Map<String, String> extra = extraHeaders.apply(callContext);
                if (extra != null) {
                    headers.putAll(extra);
                }
            }

            return headers;
        };
    }
}
