package ai.wanaku.core.config.provider.file;

import ai.wanaku.core.config.provider.api.PropertyBasedStore;
import java.net.URI;

/**
 * An abstract base class for {@link PropertyBasedStore} implementations that source their properties
 * from a file identified by a {@link URI}. This class simplifies the creation of file-based
 * configuration stores by integrating with {@link PropertyFileProvider}.
 */
abstract class FileStore extends PropertyBasedStore {

    /**
     * Constructs a new {@code FileStore} by creating a {@link PropertyFileProvider}
     * with the given {@link URI} and passing it to the superclass constructor.
     * This sets up the store to read properties from the specified file.
     *
     * @param uri The {@link URI} of the file containing the properties. Must not be {@code null}.
     */
    FileStore(URI uri) {
        super(new PropertyFileProvider(uri));
    }
}
