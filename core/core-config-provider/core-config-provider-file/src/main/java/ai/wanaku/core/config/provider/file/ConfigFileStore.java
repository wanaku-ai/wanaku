package ai.wanaku.core.config.provider.file;

import java.net.URI;

/**
 * A concrete implementation of a configuration store that reads configurations from a file.
 * This class extends {@link FileStore}, inheriting its file handling capabilities.
 */
public class ConfigFileStore extends FileStore {

    /**
     * Constructs a new {@code ConfigFileStore} by specifying the URI of the configuration file.
     *
     * @param uri The {@link URI} of the configuration file. Must not be {@code null}.
     */
    public ConfigFileStore(URI uri) {
        super(uri);
    }
}
