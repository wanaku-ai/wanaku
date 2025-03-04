package ai.wanaku.core.mcp.common.resolvers;

@FunctionalInterface
public interface AsyncRequestHandler<T> {
    void handle(T status);
}
