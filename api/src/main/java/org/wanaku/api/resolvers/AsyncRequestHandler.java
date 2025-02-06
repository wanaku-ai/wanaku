package org.wanaku.api.resolvers;

@FunctionalInterface
public interface AsyncRequestHandler<T> {
    void handle(T status);
}
