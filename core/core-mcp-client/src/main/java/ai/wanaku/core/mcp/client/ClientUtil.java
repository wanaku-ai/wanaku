package ai.wanaku.core.mcp.client;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;

public class ClientUtil {

    public static McpClient createClient(String address) {
        McpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl(address)
                .logRequests(true)
                .logResponses(true)
                .build();

        return new DefaultMcpClient.Builder().transport(transport).build();
    }
}
