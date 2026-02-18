package ai.wanaku.core.mcp.common;

import io.smallrye.common.annotation.Blocking;
import java.util.Objects;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import ai.wanaku.capabilities.sdk.api.types.CallableReference;

/**
 * Adapter that bridges ToolExecutor to the Tool interface.
 * <p>
 * This adapter allows ToolExecutor implementations to be used wherever
 * the Tool interface is expected, maintaining backward compatibility
 * while enabling separation of concerns through the composition pattern.
 * <p>
 * The adapter delegates all tool invocation calls to the underlying
 * ToolExecutor, effectively translating between the two interfaces.
 *
 * @see Tool
 * @see ToolExecutor
 */
public class ToolAdapter implements Tool {
    private final ToolExecutor executor;

    /**
     * Creates a new ToolAdapter wrapping the specified executor.
     *
     * @param executor the tool executor to delegate calls to
     * @throws NullPointerException if executor is null
     */
    public ToolAdapter(ToolExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "ToolExecutor cannot be null");
    }

    /**
     * Invokes the tool by delegating to the underlying executor.
     *
     * @param toolArguments the arguments to pass to the tool
     * @param toolReference the reference to the tool being called
     * @return a tool response containing the execution results
     */
    @Override
    @Blocking
    public ToolResponse call(ToolManager.ToolArguments toolArguments, CallableReference toolReference) {
        return executor.execute(toolArguments, toolReference);
    }
}
