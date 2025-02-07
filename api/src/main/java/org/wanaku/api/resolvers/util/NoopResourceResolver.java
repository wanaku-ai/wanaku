package org.wanaku.api.resolvers.util;

import java.io.File;
import java.util.List;

import org.wanaku.api.resolvers.AsyncRequestHandler;
import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.api.types.McpRequestStatus;
import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;

public class NoopResourceResolver implements ResourceResolver {

    @Override
    public File indexLocation() {
        return null;
    }

    @Override
    public List<McpResource> list() {
        return List.of();
    }

    @Override
    public List<McpResourceData> read(String uri) {
        return List.of();
    }

    @Override
    public void subscribe(String uri, AsyncRequestHandler<McpRequestStatus<McpResourceData>> callback) {

    }
}
