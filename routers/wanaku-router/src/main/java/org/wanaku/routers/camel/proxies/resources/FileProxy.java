package org.wanaku.routers.camel.proxies.resources;

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
import org.jboss.logging.Logger;
import org.wanaku.api.resolvers.AsyncRequestHandler;
import org.wanaku.api.types.McpRequestStatus;
import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;
import org.wanaku.api.types.ResourceReference;
import org.wanaku.routers.camel.proxies.ResourceProxy;

import static org.wanaku.core.util.IndexHelper.loadResourcesIndex;

/**
 * Proxies between MCP URIs and the Camel file component
 */
public class FileProxy implements ResourceProxy {
    private static final Logger LOG = Logger.getLogger(FileProxy.class);
    private final ConsumerTemplate consumer;

    public FileProxy(CamelContext camelContext) {
        this.consumer = camelContext.createConsumerTemplate();
    }

    @Override
    public String name() {
        return "file";
    }

    @Override
    public List<McpResource> list(File index) {
        final List<McpResource> mcpResources = new ArrayList<>();
        try {
            List<ResourceReference> references = loadResourcesIndex(index);

            // TODO: needs to filter only file related
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
        String camelUri = String.format("file://%s?fileName=%s&noop=true&idempotent=false", file.getParent(), file.getName());

        try {
            consumer.start();
            Object o = consumer.receiveBody(camelUri, 5000);
            if (o instanceof GenericFile<?> genericFile) {
                String fileName = genericFile.getAbsoluteFilePath();
                McpResourceData data = new McpResourceData();
                data.uri = uri;

                data.text = Files.readString(Path.of(fileName));
                data.mimeType = "text/plain";

                return List.of(data);
            }

            return Collections.emptyList();
        } catch (Exception e) {
            LOG.errorf("Unable to read file: %s", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            consumer.stop();
        }
    }

    @Override
    public void subscribe(String uri, AsyncRequestHandler<McpRequestStatus<McpResourceData>> callback) {
        McpRequestStatus<McpResourceData> mcpRequestStatus = new McpRequestStatus<>();

        mcpRequestStatus.status = McpRequestStatus.Status.SUBSCRIPTION_UNSUPPORTED;

        callback.handle(mcpRequestStatus);
    }
}
