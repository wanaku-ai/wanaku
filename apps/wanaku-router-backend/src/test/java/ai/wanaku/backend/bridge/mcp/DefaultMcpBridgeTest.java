package ai.wanaku.backend.bridge.mcp;

import java.util.Collections;
import java.util.List;
import ai.wanaku.backend.bridge.ForwardClient;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.types.RemoteToolReference;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultMcpBridgeTest {

    private final DefaultMcpBridge bridge = new DefaultMcpBridge();

    @Test
    void listResources_returnsEmptyList_whenServerReturnsMethodNotFound() {
        McpClient client = mock(McpClient.class);
        when(client.listResources())
                .thenThrow(new McpException(DefaultMcpBridge.JSON_RPC_METHOD_NOT_FOUND, "Method not found"));
        ForwardClient forwardClient = new ForwardClient("http://localhost:9090", client);

        List<ResourceReference> result = bridge.listResources(forwardClient);

        assertTrue(result.isEmpty());
    }

    @Test
    void listResources_returnsResources_whenServerSupportsResources() {
        McpClient client = mock(McpClient.class);
        when(client.listResources()).thenReturn(Collections.emptyList());
        ForwardClient forwardClient = new ForwardClient("http://localhost:9090", client);

        List<ResourceReference> result = bridge.listResources(forwardClient);

        assertTrue(result.isEmpty());
    }

    @Test
    void listResources_throwsServiceUnavailableException_whenServerUnreachable() {
        McpClient client = mock(McpClient.class);
        when(client.listResources()).thenThrow(new McpException(-32600, "Invalid request"));
        ForwardClient forwardClient = new ForwardClient("http://localhost:9090", client);

        ServiceUnavailableException thrown = assertThrows(
                ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException.class,
                () -> bridge.listResources(forwardClient));

        assertEquals("Service is not available at http://localhost:9090", thrown.getMessage());
    }

    @Test
    void listResources_throwsServiceUnavailableException_whenServerReturnsOtherRpcErrors() {
        McpClient client = mock(McpClient.class);
        when(client.listResources()).thenThrow(new McpException(-32600, "Invalid params"));
        ForwardClient forwardClient = new ForwardClient("http://localhost:9090", client);

        assertThrows(ServiceUnavailableException.class, () -> bridge.listResources(forwardClient));
    }

    @Test
    void listResources_throwsServiceUnavailableException_whenIoError() {
        McpClient client = mock(McpClient.class);
        when(client.listResources()).thenThrow(new RuntimeException("Connection refused"));
        ForwardClient forwardClient = new ForwardClient("http://localhost:9090", client);

        assertThrows(ServiceUnavailableException.class, () -> bridge.listResources(forwardClient));
    }

    @Test
    void listTools_returnsEmptyList_whenServerReturnsMethodNotFound() {
        McpClient client = mock(McpClient.class);
        when(client.listTools())
                .thenThrow(new McpException(DefaultMcpBridge.JSON_RPC_METHOD_NOT_FOUND, "Method not found"));
        ForwardClient forwardClient = new ForwardClient("http://localhost:9090", client);

        List<RemoteToolReference> result = bridge.listTools(forwardClient);

        assertTrue(result.isEmpty());
    }

    @Test
    void listTools_throwsServiceUnavailableException_whenServerUnreachable() {
        McpClient client = mock(McpClient.class);
        when(client.listTools()).thenThrow(new McpException(-32603, "Internal error"));
        ForwardClient forwardClient = new ForwardClient("http://localhost:9090", client);

        assertThrows(ServiceUnavailableException.class, () -> bridge.listTools(forwardClient));
    }
}
