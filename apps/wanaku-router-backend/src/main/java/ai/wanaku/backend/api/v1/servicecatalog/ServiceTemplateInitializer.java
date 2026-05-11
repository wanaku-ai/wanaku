package ai.wanaku.backend.api.v1.servicecatalog;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jboss.logging.Logger;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.capabilities.sdk.api.types.DataStore;

@ApplicationScoped
public class ServiceTemplateInitializer {
    private static final Logger LOG = Logger.getLogger(ServiceTemplateInitializer.class);
    private static final String TEMPLATES_RESOURCE = "service-templates";

    @Inject
    ServiceTemplateBean serviceTemplateBean;

    void loadBuiltInTemplates(@Observes StartupEvent ev) {
        Path templatesDir = resolveTemplatesDir();
        if (templatesDir == null || !Files.isDirectory(templatesDir)) {
            LOG.debug("No built-in service templates directory found on classpath");
            return;
        }

        int loaded = 0;
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(templatesDir, Files::isDirectory)) {
            for (Path templateDir : dirs) {
                String name = templateDir.getFileName().toString();
                try {
                    if (serviceTemplateBean.get(name) != null) {
                        LOG.debugf("Built-in template '%s' already deployed, skipping", name);
                        continue;
                    }

                    byte[] zipBytes = zipDirectory(templateDir);
                    DataStore dataStore = new DataStore();
                    dataStore.setName(name);
                    dataStore.setData(Base64.getEncoder().encodeToString(zipBytes));

                    serviceTemplateBean.deploy(dataStore);
                    loaded++;
                    LOG.infof("Deployed built-in service template: %s", name);
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to deploy built-in service template: %s", name);
                }
            }
        } catch (IOException e) {
            LOG.errorf(e, "Failed to scan built-in service templates directory");
        }

        if (loaded > 0) {
            LOG.infof("Loaded %d built-in service template(s)", loaded);
        }
    }

    private Path resolveTemplatesDir() {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(TEMPLATES_RESOURCE);
        if (resource == null) {
            return null;
        }
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            LOG.errorf(e, "Failed to resolve service templates directory");
            return null;
        }
    }

    private byte[] zipDirectory(Path dir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            try (var walker = Files.walk(dir)) {
                walker.filter(Files::isRegularFile).forEach(file -> {
                    String entryName = dir.relativize(file).toString();
                    try {
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(file, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeIOException(e);
                    }
                });
            }
        } catch (RuntimeIOException e) {
            throw e.getCause();
        }
        return baos.toByteArray();
    }

    private static class RuntimeIOException extends RuntimeException {
        RuntimeIOException(IOException cause) {
            super(cause);
        }

        @Override
        public IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}
