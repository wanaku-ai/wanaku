package ai.wanaku.core.config.provider.file;

import ai.wanaku.core.config.provider.api.SecretStore;
import java.net.URI;

/**
 * A concrete implementation of a secret store that reads secrets from a file.
 * This class extends {@link FileStore}, inheriting its file-based property loading,
 * and implements the {@link SecretStore} interface to provide secret-specific access.
 */
public class SecretFileStore extends FileStore implements SecretStore {

    /**
     * Constructs a new {@link SecretFileStore} by specifying the URI of the secret file.
     * The secrets will be loaded from this file.
     *
     * @param uri The {@link URI} of the file containing the secrets. Must not be {@code null}.
     */
    public SecretFileStore(URI uri) {
        super(uri);
    }
}
