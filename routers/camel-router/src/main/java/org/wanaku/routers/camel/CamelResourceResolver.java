package org.wanaku.routers.camel;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;
import org.wanaku.api.types.ResourceReference;

class CamelResourceResolver implements ResourceResolver {
    private static final Logger LOG = Logger.getLogger(CamelResourceResolver.class);
    private final String resourcesPath;
    private final Map<String, ? extends ResourceProxy> proxies;

    public CamelResourceResolver(String resourcesPath, Map<String, ? extends ResourceProxy> proxies) {
        this.resourcesPath = resourcesPath;
        this.proxies = proxies;
    }

    public static List<ResourceReference> loadResources(File resourcesFile) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(resourcesFile,
                objectMapper.getTypeFactory().constructCollectionType(List.class, ResourceReference.class));
    }

    @Override
    public List<McpResource> resources() {
        LOG.info("Resolving resources");
        List<McpResource> mcpResources = new ArrayList<>();

        File resourcesFile = new File(resourcesPath, "resources.json");

        try {
            List<ResourceReference> references = loadResources(resourcesFile);

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
        String scheme = uriUri.getScheme();

        ResourceProxy resourceProxy = proxies.get(scheme);
        LOG.infof("Using the resource proxy %s to evaluate MCP uri %s", resourceProxy.name(), uri);

        return resourceProxy.eval(uri);
    }
}
