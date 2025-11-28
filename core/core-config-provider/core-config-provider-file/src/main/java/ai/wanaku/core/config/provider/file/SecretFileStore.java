package ai.wanaku.core.config.provider.file;

import ai.wanaku.core.config.provider.api.PropertyBasedStore;
import ai.wanaku.core.config.provider.api.PropertyProvider;
import ai.wanaku.core.config.provider.api.SecretStore;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Reads secrets from files, automatically decrypting if encryption is configured.
 * <p>
 * Set environment variables WANAKU_SECRETS_ENCRYPTION_PASSWORD and
 * WANAKU_SECRETS_ENCRYPTION_SALT to enable decryption.
 */
public class SecretFileStore extends PropertyBasedStore implements SecretStore {

    private static final String ENV_PASSWORD = "WANAKU_SECRETS_ENCRYPTION_PASSWORD";
    private static final String ENV_SALT = "WANAKU_SECRETS_ENCRYPTION_SALT";

    public SecretFileStore(URI uri) {
        super(new DecryptingPropertyProvider(uri));
    }

    private static class DecryptingPropertyProvider implements PropertyProvider {
        private final Properties properties = new Properties();

        DecryptingPropertyProvider(URI uri) {
            if (uri == null) {
                return;
            }
            try {
                byte[] data = Files.readAllBytes(Paths.get(uri));

                String password = System.getenv(ENV_PASSWORD);
                String salt = System.getenv(ENV_SALT);

                if (password != null && !password.isEmpty() && salt != null && !salt.isEmpty()) {
                    data = EncryptionHelper.decrypt(data, password, salt);
                }

                // Decode the secret file as UTF-8 and load via a Reader so we are not bound
                // to the ISO-8859-1 encoding required by Properties.load(InputStream).
                String content = new String(data, StandardCharsets.UTF_8);
                properties.load(new StringReader(content));
            } catch (Exception e) {
                throw new RuntimeException("Failed to load secrets from " + uri, e);
            }
        }

        @Override
        public Properties getProperties() {
            return properties;
        }
    }
}
