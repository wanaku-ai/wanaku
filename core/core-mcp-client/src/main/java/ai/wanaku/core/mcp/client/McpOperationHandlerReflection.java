package ai.wanaku.core.mcp.client;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.mcp.client.McpRoot;
import dev.langchain4j.mcp.client.logging.McpLogMessage;
import dev.langchain4j.mcp.client.progress.McpProgressHandler;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;

/**
 * Centralizes the reflection logic required to extract internal fields from
 * {@link McpOperationHandler} and to register Jackson mix-ins on static
 * {@code OBJECT_MAPPER} fields in langchain4j-mcp transport classes.
 *
 * <p>This class exists because langchain4j-mcp does not expose the internal
 * state of {@code McpOperationHandler} through public API, so transport
 * decorators (e.g. {@link SamplingMcpTransport}, {@link ElicitationMcpTransport})
 * must use reflection to reconstruct a handler with the same wiring but an
 * overridden {@code handle()} method. Centralizing this logic here ensures
 * that version-related reflection failures are logged clearly and fail fast
 * rather than surfacing as obscure runtime errors.</p>
 */
public final class McpOperationHandlerReflection {

    private static final Logger LOG = LoggerFactory.getLogger(McpOperationHandlerReflection.class);

    private static final String[] OBJECT_MAPPER_HOLDER_CLASSES = {
        "dev.langchain4j.mcp.client.DefaultMcpClient",
        "dev.langchain4j.mcp.client.transport.http.HttpMcpTransport",
        "dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport"
    };

    private McpOperationHandlerReflection() {}

    /**
     * Extracts the internal state from an existing {@link McpOperationHandler}
     * and creates a new one that delegates all non-intercepted messages to the
     * original handler's {@code handle()} method, while routing intercepted
     * method calls through the provided {@code interceptingHandle} consumer.
     *
     * @param original       the original handler whose fields will be extracted
     * @param transport      the transport reference to embed in the new handler
     *                       (typically the decorating transport)
     * @param interceptingHandle a consumer that receives {@link JsonNode} messages
     *                       matching the intercepted method; it is called instead
     *                       of the original handler for those messages
     * @param interceptedMethod the JSON-RPC method name to intercept (e.g.
     *                       {@code "sampling/createMessage"} or
     *                       {@code "elicitation/create"})
     * @return a new {@link McpOperationHandler} with the intercepted handle logic
     * @throws IllegalStateException if any required field cannot be extracted
     *                               from the original handler (i.e. the
     *                               langchain4j-mcp version has changed)
     */
    @SuppressWarnings("unchecked")
    public static McpOperationHandler createInterceptingHandler(
            McpOperationHandler original,
            McpTransport transport,
            Consumer<JsonNode> interceptingHandle,
            String interceptedMethod) {

        Map<Long, CompletableFuture<JsonNode>> pendingOperations = extractField(original, "pendingOperations");
        Supplier<List<McpRoot>> roots = extractField(original, "roots");
        Consumer<McpLogMessage> logMessageConsumer = extractField(original, "logMessageConsumer");
        Runnable onToolListUpdate = extractField(original, "onToolListUpdate");
        Runnable onResourceListUpdate = extractField(original, "onResourceListUpdate");
        Runnable onPromptListUpdate = extractField(original, "onPromptListUpdate");
        Consumer<String> onResourceUpdate = extractField(original, "onResourceUpdate");
        McpProgressHandler progressHandler = extractField(original, "progressHandler");

        return new McpOperationHandler(
                pendingOperations,
                roots,
                transport,
                logMessageConsumer,
                onToolListUpdate,
                onResourceListUpdate,
                onPromptListUpdate,
                onResourceUpdate,
                progressHandler) {
            @Override
            public void handle(JsonNode node) {
                if (node.has("method")
                        && interceptedMethod.equals(node.get("method").asText())) {
                    interceptingHandle.accept(node);
                } else {
                    original.handle(node);
                }
            }
        };
    }

    /**
     * Registers a Jackson mix-in on the static {@code OBJECT_MAPPER} fields of
     * known langchain4j-mcp classes to inject additional serialization behavior
     * (e.g. the elicitation capability into MCP initialization requests).
     *
     * <p>Each candidate class is attempted independently; if a class is not on
     * the classpath or does not have the expected static field, it is silently
     * skipped. If the field exists but cannot be accessed, a warning is logged.</p>
     *
     * @param mixInTargetClass the class to which the mix-in should be applied
     * @param mixInClass       the mix-in interface/class that defines the
     *                         additional or overridden Jackson annotations
     * @return the number of classes where the mix-in was successfully registered
     */
    public static int registerObjectMapperMixIn(Class<?> mixInTargetClass, Class<?> mixInClass) {
        int registered = 0;
        for (String className : OBJECT_MAPPER_HOLDER_CLASSES) {
            try {
                Class<?> clazz = Class.forName(className);
                Field field = clazz.getDeclaredField("OBJECT_MAPPER");
                field.setAccessible(true);
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        (com.fasterxml.jackson.databind.ObjectMapper) field.get(null);
                mapper.addMixIn(mixInTargetClass, mixInClass);
                registered++;
                LOG.debug("Registered mix-in {} on {} OBJECT_MAPPER", mixInClass.getSimpleName(), className);
            } catch (ClassNotFoundException e) {
                LOG.debug("Class {} not on classpath, skipping mix-in registration", className);
            } catch (NoSuchFieldException e) {
                LOG.warn(
                        "Class {} does not have OBJECT_MAPPER field; "
                                + "langchain4j-mcp version may have changed. "
                                + "Mix-in registration skipped for this class.",
                        className);
            } catch (IllegalAccessException e) {
                LOG.warn(
                        "Cannot access OBJECT_MAPPER field on {}; " + "mix-in registration skipped: {}",
                        className,
                        e.getMessage());
            } catch (Exception e) {
                LOG.warn("Unexpected error registering mix-in on {}: {}", className, e.getMessage());
            }
        }
        if (registered == 0) {
            LOG.warn(
                    "No OBJECT_MAPPER instances found for mix-in registration; "
                            + "langchain4j-mcp version may have changed. "
                            + "Expected fields on: {}",
                    String.join(", ", OBJECT_MAPPER_HOLDER_CLASSES));
        }
        return registered;
    }

    @SuppressWarnings("unchecked")
    private static <T> T extractField(McpOperationHandler handler, String fieldName) {
        try {
            Field field = handler.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(handler);
        } catch (NoSuchFieldException e) {
            String msg = "McpOperationHandler does not have field '" + fieldName
                    + "'; langchain4j-mcp version may have changed. "
                    + "Please update the reflection logic in McpOperationHandlerReflection.";
            LOG.error(msg);
            throw new IllegalStateException(msg, e);
        } catch (IllegalAccessException e) {
            String msg = "Cannot access field '" + fieldName + "' on McpOperationHandler: " + e.getMessage();
            LOG.error(msg);
            throw new IllegalStateException(msg, e);
        }
    }
}
