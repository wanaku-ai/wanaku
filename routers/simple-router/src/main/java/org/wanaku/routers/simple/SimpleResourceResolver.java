package org.wanaku.routers.simple;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;
import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;
import org.wanaku.api.types.ResourceReference;

import static org.wanaku.core.util.ResourcesHelper.loadIndex;

class SimpleResourceResolver implements ResourceResolver {
    private static final Logger LOG = Logger.getLogger(SimpleResourceResolver.class);
    private static final String INDEX_FILE = "resources.json";
    private String resourcesPath;

    public SimpleResourceResolver(String resourcesPath) {
        this.resourcesPath = resourcesPath;
    }

    @Override
    public File indexLocation() {
        return new File(resourcesPath, INDEX_FILE);
    }

    @Override
    public List<McpResource> resources() {
        LOG.info("Resolving resources");
        List<McpResource> mcpResources = new ArrayList<>();

        File resourcesFile = indexLocation();

        try {
            List<ResourceReference> references = loadIndex(resourcesFile);

            for (ResourceReference reference : references) {
                McpResource mcpResource = new McpResource();

                mcpResource.uri = String.format("%s:%s", reference.getType(), reference.getLocation());
                mcpResource.name = reference.getName();
                mcpResource.mimeType = reference.getMimeType();
                mcpResource.description = reference.getDescription();

                mcpResources.add(mcpResource);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return mcpResources;
    }

    @Override
    public List<McpResourceData> read(String uri) {
        URI uriUri = URI.create(uri);
        String filePath = uriUri.getSchemeSpecificPart();

        File file = new File(filePath);
        try {
            McpResourceData data = new McpResourceData();
            data.uri = uri;
            data.text = Files.readString(file.toPath());
            data.mimeType = "text/plain";

            return List.of(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
