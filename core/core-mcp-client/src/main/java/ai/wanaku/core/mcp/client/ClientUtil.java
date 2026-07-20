package ai.wanaku.core.mcp.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;

public class ClientUtil {

    public static McpClient createClient(String address) {
        return createClient(address, (McpHeadersSupplier) null, null);
    }

    public static McpClient createClient(String address, List<McpRoot> roots) {
        return createClient(address, (McpHeadersSupplier) null, roots);
    }

    public static McpClient createClient(String address, String token) {
        return createClient(address, token, null);
    }

    public static McpClient createClient(String address, String token, List<McpRoot> roots) {
        String normalizedToken = token != null ? token.trim() : null;
        McpHeadersSupplier tokenHeaders = (normalizedToken != null && !normalizedToken.isEmpty())
                ? callContext -> Map.of("Authorization", "Bearer " + normalizedToken)
                : null;
        return createClient(address, tokenHeaders, roots);
    }

    public static McpClient createClient(String address, McpHeadersSupplier headersSupplier) {
        return createClient(address, headersSupplier, null);
    }

    public static McpClient createClient(String address, McpHeadersSupplier headersSupplier, List<McpRoot> roots) {
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

        DefaultMcpClient.Builder clientBuilder = new DefaultMcpClient.Builder().transport(transport);
        if (roots != null && !roots.isEmpty()) {
            clientBuilder.roots(roots);
        }
        return clientBuilder.build();
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
