package org.wanaku.server.quarkus.api;

import java.util.List;

import org.wanaku.server.quarkus.types.McpResource;

public interface ResourceResolver extends Resolver {
    List<McpResource> resources();
}
