package ai.wanaku.core.util.support;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Shared Tavily service catalog fixture used by integration-style tests.
 *
 * <p>The fixture is kept in sync with the actual catalog artifacts under
 * {@code service-catalogs/tavily} so tests do not duplicate paths, dependency
 * coordinates, or manifest fields.</p>
 */
public final class TavilyServiceCatalogHelper {
    public static final String CATALOG_RELATIVE_DIR = "service-catalogs/tavily";
    public static final String CATALOG_INDEX_FILE = "index.properties";
    public static final String CATALOG_NAME;
    public static final String CATALOG_DESCRIPTION;
    public static final String CATALOG_ICON;
    public static final String SERVICE_NAME;
    public static final String ROUTES_FILE_NAME;
    public static final String RULES_FILE_NAME;
    public static final String DEPENDENCIES_FILE_NAME;
    public static final String ROUTES_FILE;
    public static final String RULES_FILE;
    public static final String DEPENDENCIES_FILE;

    private static final Properties CATALOG_PROPERTIES = loadCatalogProperties();

    static {
        CATALOG_NAME = property("catalog.name");
        CATALOG_DESCRIPTION = property("catalog.description");
        CATALOG_ICON = property("catalog.icon");
        SERVICE_NAME = property("catalog.services");
        ROUTES_FILE = property("catalog.routes." + SERVICE_NAME);
        RULES_FILE = property("catalog.rules." + SERVICE_NAME);
        DEPENDENCIES_FILE = property("catalog.dependencies." + SERVICE_NAME);
        ROUTES_FILE_NAME = fileName(ROUTES_FILE);
        RULES_FILE_NAME = fileName(RULES_FILE);
        DEPENDENCIES_FILE_NAME = fileName(DEPENDENCIES_FILE);
    }

    private TavilyServiceCatalogHelper() {}

    public static void populateCatalogDirectory(Path destination) throws IOException {
        Files.createDirectories(destination);
        copyCatalogFixture(destination);
    }

    public static String createCatalogBase64() throws IOException {
        return Base64.getEncoder().encodeToString(createCatalogZip());
    }

    public static byte[] createCatalogZip() throws IOException {
        Path source = findCatalogRoot();
        try (var baos = new java.io.ByteArrayOutputStream();
                var zos = new java.util.zip.ZipOutputStream(baos)) {
            for (Path entry : catalogEntries(source)) {
                String entryName = source.relativize(entry).toString().replace('\\', '/');
                try {
                    if (Files.isDirectory(entry)) {
                        zos.putNextEntry(new java.util.zip.ZipEntry(entryName + "/"));
                    } else {
                        zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                        Files.copy(entry, zos);
                    }
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return baos.toByteArray();
        }
    }

    public static Path findCatalogRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(CATALOG_RELATIVE_DIR).resolve(CATALOG_INDEX_FILE);
            if (Files.exists(candidate)) {
                return current.resolve(CATALOG_RELATIVE_DIR);
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate Tavily service catalog fixture");
    }

    public static void copyCatalogFixture(Path destination) throws IOException {
        Path source = findCatalogRoot();
        Files.createDirectories(destination);
        for (Path entry : catalogEntries(source)) {
            Path target = destination.resolve(source.relativize(entry).toString());
            if (Files.isDirectory(entry)) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(Objects.requireNonNull(target.getParent()));
                Files.copy(entry, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Properties loadCatalogProperties() {
        Path index = findCatalogRoot().resolve(CATALOG_INDEX_FILE);
        try (var reader =
                new InputStreamReader(Files.newInputStream(index), java.nio.charset.StandardCharsets.ISO_8859_1)) {
            Properties props = new Properties();
            props.load(reader);
            return props;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String property(String key) {
        String value = CATALOG_PROPERTIES.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing catalog property: " + key);
        }
        return value;
    }

    private static String fileName(String relativePath) {
        return Path.of(relativePath).getFileName().toString();
    }

    private static List<Path> catalogEntries(Path source) throws IOException {
        List<Path> entries = new java.util.ArrayList<>();
        entries.add(source.resolve(CATALOG_INDEX_FILE));
        try (var walk = Files.walk(source.resolve(SERVICE_NAME))) {
            walk.forEach(entries::add);
        }
        entries.sort(Comparator.naturalOrder());
        return entries;
    }
}
