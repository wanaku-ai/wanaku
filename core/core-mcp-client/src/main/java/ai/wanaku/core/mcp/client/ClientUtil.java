package ai.wanaku.core.mcp.client;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;

public class ClientUtil {

    public static McpClient createClient(String address) {
        return createClient(address, null);
    }

    public static McpClient createClient(String address, McpSamplingHandler samplingHandler) {
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

        return new DefaultMcpClient.Builder().transport(transport).build();
    }
}
