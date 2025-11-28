package ai.wanaku.core.config.provider.file;

import ai.wanaku.core.config.provider.api.SecretWriter;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes secrets to files, optionally encrypting them.
 * <p>
 * Set environment variables WANAKU_SECRETS_ENCRYPTION_PASSWORD and
 * WANAKU_SECRETS_ENCRYPTION_SALT to enable encryption.
 */
public class FileSecretWriter implements SecretWriter {

    private static final String ENV_PASSWORD = "WANAKU_SECRETS_ENCRYPTION_PASSWORD";
    private static final String ENV_SALT = "WANAKU_SECRETS_ENCRYPTION_SALT";

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
            Path path = Paths.get(serviceHome.getAbsolutePath(), id);
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);

            String password = System.getenv(ENV_PASSWORD);
            String salt = System.getenv(ENV_SALT);

            if (password != null && !password.isEmpty() && salt != null && !salt.isEmpty()) {
                bytes = EncryptionHelper.encrypt(bytes, password, salt);
            }

            Files.write(path, bytes);
            return path.toUri();
        } catch (Exception e) {
            throw new RuntimeException("Failed to write secret", e);
        }
    }
}
