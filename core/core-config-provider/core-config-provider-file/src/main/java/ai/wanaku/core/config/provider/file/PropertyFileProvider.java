package ai.wanaku.core.config.provider.file;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.core.config.provider.api.PropertyProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.Properties;

/**
 * A concrete implementation of {@link PropertyProvider} that loads properties from a file
 * specified by a {@link URI}. This provider handles reading properties from a standard
 * Java properties file format.
 */
public class PropertyFileProvider implements PropertyProvider {
    private final Properties properties;

    /**
     * Constructs a new {@link PropertyFileProvider} and attempts to load properties from
     * the file specified by the given {@link URI}.
     * If the URI is {@code null}, an empty {@link Properties} object is initialized.
     *
     * @param uri The {@link URI} of the properties file to load. If {@code null},
     * an empty {@link Properties} object will be used.
     * @throws WanakuException If the file specified by the URI is not found, or
     * if an I/O error occurs during loading.
     */
    public PropertyFileProvider(URI uri) {
        if (uri == null) {
            properties = new Properties();
        } else {
            final String path = uri.getPath();

            File file = new File(path);
            properties = new Properties();
            try (FileInputStream fis = new FileInputStream(file)) {
                properties.load(fis);
            } catch (IOException e) {
                throw new WanakuException(e);
            }
        }
    }

    @Override
    public Properties getProperties() {
        return properties;
    }
}
