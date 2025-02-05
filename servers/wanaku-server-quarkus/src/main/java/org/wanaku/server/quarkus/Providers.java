package org.wanaku.server.quarkus;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.wanaku.server.quarkus.api.ResourceResolver;
import org.wanaku.server.quarkus.types.McpResource;
import org.wanaku.server.quarkus.types.McpResourceData;
import org.wanaku.server.quarkus.types.ResourceReference;
import picocli.CommandLine;

@ApplicationScoped
public class Providers {
    private static final Logger LOG = Logger.getLogger(Providers.class);

    @Inject
    CommandLine.ParseResult parseResult;
    @Inject org.wanaku.server.quarkus.McpResource mcpResource;

    @Produces
    ResourceResolver getResourceResolver() {
        var resourcesPath = parseResult.matchedOption("resources-path").getValue().toString();
        return new SimpleResourceResolver(resourcesPath);
    }

    private static class SimpleResourceResolver implements ResourceResolver {
        private String resourcesPath;

        public SimpleResourceResolver(String resourcesPath) {
            this.resourcesPath = resourcesPath;
        }

        public static List<ResourceReference> loadResources(File resourcesFile) throws Exception {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(resourcesFile, objectMapper.getTypeFactory().constructCollectionType(List.class, ResourceReference.class));
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
}
