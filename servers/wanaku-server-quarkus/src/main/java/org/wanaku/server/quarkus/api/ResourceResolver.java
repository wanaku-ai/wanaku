package org.wanaku.server.quarkus.api;

import java.util.List;

import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;

public interface ResourceResolver extends Resolver {
    List<McpResource> resources();

    List<McpResourceData> read(String uri);
}
