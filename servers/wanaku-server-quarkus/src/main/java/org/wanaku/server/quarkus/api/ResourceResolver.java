package org.wanaku.server.quarkus.api;

import java.util.List;

import org.wanaku.server.quarkus.types.McpResource;
import org.wanaku.server.quarkus.types.McpResourceData;

public interface ResourceResolver extends Resolver {
    List<McpResource> resources();

    List<McpResourceData> read(String uri);
}
