package ai.wanaku.core.mcp.client;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;

public class ClientUtil {

    public static McpClient createClient(String address) {
        return createClient(address, null, null);
    }

    public static McpClient createClient(String address, McpSamplingHandler samplingHandler) {
        return createClient(address, samplingHandler, null);
    }

    /**
     * Creates a new {@link McpClient} with optional sampling and elicitation support.
     * <p>
     * When a non-null {@code samplingHandler} is provided, the transport is wrapped with
     * {@link SamplingMcpTransport} to handle server-initiated {@code sampling/createMessage}
     * requests.
     * <p>
     * When a non-null {@code elicitationHandler} is provided, the transport is wrapped with
     * {@link ElicitationMcpTransport} to handle server-initiated {@code elicitation/create}
     * requests.
     *
     * @param address            the remote MCP server address
     * @param samplingHandler    optional handler for sampling requests (may be {@code null})
     * @param elicitationHandler optional handler for elicitation requests (may be {@code null})
     * @return a new {@link McpClient} instance
     */
    public static McpClient createClient(
            String address, McpSamplingHandler samplingHandler, McpElicitationHandler elicitationHandler) {
        McpTransport transport;
        if (address.endsWith("sse") || address.endsWith("sse/")) {
            transport = new HttpMcpTransport.Builder()
                    .sseUrl(address)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        } else {
            transport = new StreamableHttpMcpTransport.Builder()
                    .url(address)
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        }

        if (samplingHandler != null) {
            transport = new SamplingMcpTransport(transport, samplingHandler);
        }

        if (elicitationHandler != null) {
            transport = new ElicitationMcpTransport(transport, elicitationHandler);
        }

        return new DefaultMcpClient.Builder().transport(transport).build();
    }
}
