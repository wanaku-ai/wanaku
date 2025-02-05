package org.wanaku.server.quarkus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.wanaku.server.quarkus.api.ResourceResolver;
import org.wanaku.server.quarkus.types.McpResource;
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
        return new ResourceResolver() {

            public static List<ResourceReference> loadResources(File resourcesFile) throws Exception {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue(resourcesFile, objectMapper.getTypeFactory().constructCollectionType(List.class, ResourceReference.class));
            }

            @Override
            public List<McpResource> resources() {
                LOG.info("Resolving resources");
                List<McpResource> mcpResources = new ArrayList<>();

                String resourcesPath = parseResult.matchedOption("resources-path").getValue().toString();
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
        };
    }
}
