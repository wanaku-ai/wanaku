package org.wanaku.routers.camel.translators;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.component.file.GenericFile;
import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;
import org.wanaku.api.types.ResourceReference;
import org.wanaku.routers.camel.ResourceProxy;

import static org.wanaku.routers.camel.ResourcesHelper.loadResources;

/**
 * Proxies between MCP URIs and the Camel file component
 */
public class FileProxy implements ResourceProxy {
    private final ConsumerTemplate consumer;

    public FileProxy(CamelContext camelContext) {
        this.consumer = camelContext.createConsumerTemplate();
        consumer.start();
    }

    @Override
    public String name() {
        return "file";
    }

    @Override
    public List<McpResource> list(File resourceIndex) {
        List<McpResource> mcpResources = new ArrayList<>();
        try {
            List<ResourceReference> references = loadResources(resourceIndex);

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
    public List<McpResourceData> eval(String uri) {
        URI uriUri = URI.create(uri);
        String filePath = uriUri.getSchemeSpecificPart();

        File file = new File(filePath);
        String camelUri = String.format("file://%s?fileName=%s", file.getParent(), file.getName());

        try {
            Object o = consumer.receiveBody(camelUri, 5000);
            if (o instanceof GenericFile<?> genericFile) {
                String fileName = genericFile.getFileName();
                McpResourceData data = new McpResourceData();
                data.uri = uri;

                data.text = Files.readString(Path.of(fileName));
                data.mimeType = "text/plain";

                return List.of(data);
            }

            return Collections.emptyList();
        } catch (Throwable e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
