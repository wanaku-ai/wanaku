package ai.wanaku.mcp.inspector;

import io.quarkiverse.mcp.server.sse.client.SseClient;
import io.quarkiverse.mcp.server.test.McpSseClient;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Manages MCP server connections, including SSE client setup, REST client configuration,
 * and session initialization.
 */
public class McpConnectionManager {
    private static final Logger LOG = LoggerFactory.getLogger(McpConnectionManager.class);

    private final String mcpUri;
    private final String mcpContextPath;
    private final McpMessageFactory messageFactory;

    public McpConnectionManager(String mcpUri, String mcpContextPath, McpMessageFactory messageFactory) {
        this.mcpUri = mcpUri;
        this.mcpContextPath = mcpContextPath;
        this.messageFactory = messageFactory;
    }

    /**
     * Establishes a connection to the MCP server and initializes the session.
     *
     * @return ConnectionResult containing the established connections and session ID
     * @throws Exception if connection or initialization fails
     */
    public ConnectionResult connect() throws Exception {
        LOG.info("Connecting to MCP server at: {}{}", mcpUri, mcpContextPath);

        // Create and connect SSE client
        McpSseClient sseClient = new McpSseClient(URI.create(mcpUri + mcpContextPath));
        sseClient.connect();

        // Wait for first event to get session information
        SseClient.SseEvent firstEvent = sseClient.waitForFirstEvent();
        LOG.debug("Received first SSE event: {}", firstEvent.data());

        // Create REST client
        McpServerRestClient restClient = RestClientBuilder.newBuilder()
                .baseUri(mcpUri)
                .build(McpServerRestClient.class);

        // Extract session ID and initialize session
        String sessionId = extractSessionId(firstEvent);
        initializeSession(sseClient, restClient, sessionId);

        LOG.info("Successfully connected to MCP server with session ID: {}", sessionId);

        return new ConnectionResult(sseClient, restClient, sessionId);
    }

    /**
     * Extracts the session ID from the first SSE event.
     *
     * @param firstEvent The first SSE event received
     * @return The extracted session ID
     */
    private String extractSessionId(SseClient.SseEvent firstEvent) {
        String eventData = firstEvent.data().strip();
        String sessionId = eventData.substring(eventData.lastIndexOf('/') + 1);
        LOG.debug("Extracted session ID: {}", sessionId);
        return sessionId;
    }

    /**
     * Initializes the MCP session by sending initialize and initialized messages.
     *
     * @param sseClient The SSE client
     * @param restClient The REST client
     * @param sessionId The session ID
     */
    private void initializeSession(McpSseClient sseClient, McpServerRestClient restClient, String sessionId) {
        LOG.debug("Initializing MCP session: {}", sessionId);

        // Send initialize message
        int initId = sseClient.nextRequestId();
        JsonObject initMessage = messageFactory.createInitializeMessage(initId);

        restClient.messages(sessionId, initMessage);

        // Wait for response and log it
        JsonObject initResponse = sseClient.waitForLastResponse();
        LOG.info("MCP initialization response: {}", initResponse);

        // Send initialized notification
        int notificationId = sseClient.nextRequestId();
        JsonObject initializedNotification = messageFactory.createInitializedNotification(notificationId);

        restClient.messages(sessionId, initializedNotification);

        LOG.debug("MCP session initialization completed");
    }

    /**
         * Result object containing the established connections and session information.
         */
        public record ConnectionResult(McpSseClient client, McpServerRestClient restClient, String sessionId) {
    }
}