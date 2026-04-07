package ai.wanaku.cli.main.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipHelperTest {

    @TempDir
    Path tempDir;

    @Test
    void unzipKeepsEntriesWithinTheComponentDirectory() throws IOException {
        Path zipFile = tempDir.resolve("safe.zip");
        createZip(zipFile, "component-1.0.0/bin/app.sh", "component-1.0.0/config/app.yml");

        ZipHelper.unzip(zipFile.toFile(), tempDir.toFile(), "component");

        Path appScript = tempDir.resolve("component/bin/app.sh");
        Path configFile = tempDir.resolve("component/config/app.yml");

        assertTrue(Files.exists(appScript));
        assertTrue(Files.exists(configFile));
        assertFalse(Files.exists(tempDir.resolve("bin/app.sh")));
        assertFalse(Files.exists(tempDir.resolve("config/app.yml")));
    }

    @Test
    void unzipRejectsPathTraversalEntries() throws IOException {
        Path zipFile = tempDir.resolve("malicious.zip");
        createZip(zipFile, "component-1.0.0/bin/app.sh", "component-1.0.0/..\\evil.txt");

        ZipHelper.unzip(zipFile.toFile(), tempDir.toFile(), "component");

        assertTrue(Files.exists(tempDir.resolve("component/bin/app.sh")));
        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
        assertFalse(Files.exists(tempDir.getParent().resolve("evil.txt")));
    }

    @Test
    void unzipRejectsForwardSlashPathTraversalEntries() throws IOException {
        Path zipFile = tempDir.resolve("malicious-forward-slash.zip");
        createZip(zipFile, "component-1.0.0/bin/app.sh", "component-1.0.0/../evil.txt");

        ZipHelper.unzip(zipFile.toFile(), tempDir.toFile(), "component");

        assertTrue(Files.exists(tempDir.resolve("component/bin/app.sh")));
        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
        assertFalse(Files.exists(tempDir.getParent().resolve("evil.txt")));
    }

    @Test
    void unzipKeepsFlatFilesInsideTheComponentDirectory() throws IOException {
        Path zipFile = tempDir.resolve("flat-file.zip");
        createZip(zipFile, "README.txt");

        ZipHelper.unzip(zipFile.toFile(), tempDir.toFile(), "component");

        assertTrue(Files.exists(tempDir.resolve("component/README.txt")));
        assertFalse(Files.exists(tempDir.resolve("README.txt")));
    }

    @Test
    void unzipKeepsDeeplyNestedEntriesInsideTheComponentDirectory() throws IOException {
        Path zipFile = tempDir.resolve("nested.zip");
        createZip(zipFile, "component-1.0.0/lib/internal/config/app.yml");

        ZipHelper.unzip(zipFile.toFile(), tempDir.toFile(), "component");

        assertTrue(Files.exists(tempDir.resolve("component/lib/internal/config/app.yml")));
        assertFalse(Files.exists(tempDir.resolve("lib/internal/config/app.yml")));
    }

    @Test
    void unzipRejectsDeepPathTraversalEntries() throws IOException {
        Path zipFile = tempDir.resolve("deep-traversal.zip");
        createZip(zipFile, "component-1.0.0/lib/../../evil.txt");

        ZipHelper.unzip(zipFile.toFile(), tempDir.toFile(), "component");

        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
        assertFalse(Files.exists(tempDir.getParent().resolve("evil.txt")));
    }

    private static void createZip(Path zipFile, String... entryNames) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (String entryName : entryNames) {
                out.putNextEntry(new ZipEntry(entryName));
                out.write(("content-for-" + entryName).getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }
}
