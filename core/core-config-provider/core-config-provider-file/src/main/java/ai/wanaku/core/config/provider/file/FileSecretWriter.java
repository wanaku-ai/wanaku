package ai.wanaku.core.config.provider.file;

import ai.wanaku.core.config.provider.api.SecretWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileSecretWriter implements SecretWriter {
    protected final File serviceHome;

    public FileSecretWriter(String serviceHome) {
        this(new File(serviceHome));
    }

    public FileSecretWriter(File serviceHome) {
        this.serviceHome = serviceHome;
    }

    @Override
    public URI write(String id, String data) {
        try {
            final Path path = Paths.get(serviceHome.getAbsolutePath(), id);
            Files.write(path, data.getBytes());

            return path.toUri();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
