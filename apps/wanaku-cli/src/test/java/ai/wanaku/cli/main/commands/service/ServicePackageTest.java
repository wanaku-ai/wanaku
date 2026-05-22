package ai.wanaku.cli.main.commands.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "https://github.com/wanaku-ai/wanaku/issues/1193")
class ServicePackageTest {

    @TempDir
    Path tempDir;

    @Test
    void testPackageResolvesInMemoryWithoutMutatingSourceFiles() throws Exception {
        File rootDir = tempDir.resolve("catalog").toFile();
        assertTrue(rootDir.mkdirs());

        createCatalogSkeleton(rootDir);

        File outputFile = tempDir.resolve("catalog.b64").toFile();

        ServicePackage cmd = new ServicePackage();
        setField(cmd, "path", rootDir.getAbsolutePath());
        setField(cmd, "output", outputFile.getAbsolutePath());
        setField(cmd, "resolveVersions", true);

        Integer result = cmd.call();
        assertEquals(0, result);

        String depsContent;
        try (FileInputStream fis = new FileInputStream(new File(rootDir, "sys1/sys1.dependencies.txt"))) {
            depsContent = new String(fis.readAllBytes());
        }
        assertTrue(depsContent.contains("${camel.version}"));
        assertTrue(outputFile.exists());

        String base64Zip;
        try (FileInputStream fis = new FileInputStream(outputFile)) {
            base64Zip = new String(fis.readAllBytes());
        }

        byte[] resolvedZip = java.util.Base64.getDecoder().decode(base64Zip);
        String resolvedDeps = extractZipEntry(resolvedZip, "sys1/sys1.dependencies.txt");
        assertTrue(resolvedDeps.contains("org.apache.camel:camel-jms:4.18.2"));
        assertFalse(resolvedDeps.contains("${camel.version}"));
    }

    private void createCatalogSkeleton(File rootDir) throws Exception {
        Properties props = new Properties();
        props.setProperty("catalog.name", "catalog");
        props.setProperty("catalog.description", "test");
        props.setProperty("catalog.services", "sys1");
        props.setProperty("catalog.versions", "versions.properties");
        props.setProperty("catalog.routes.sys1", "sys1/sys1.camel.yaml");
        props.setProperty("catalog.rules.sys1", "sys1/sys1.wanaku-rules.yaml");
        props.setProperty("catalog.dependencies.sys1", "sys1/sys1.dependencies.txt");

        try (FileOutputStream fos = new FileOutputStream(new File(rootDir, "index.properties"))) {
            props.store(fos, null);
        }

        File systemDir = new File(rootDir, "sys1");
        assertTrue(systemDir.mkdirs());

        try (FileOutputStream fos = new FileOutputStream(new File(systemDir, "sys1.camel.yaml"))) {
            fos.write("route".getBytes());
        }

        try (FileOutputStream fos = new FileOutputStream(new File(systemDir, "sys1.wanaku-rules.yaml"))) {
            fos.write("rules".getBytes());
        }

        try (FileOutputStream fos = new FileOutputStream(new File(systemDir, "sys1.dependencies.txt"))) {
            fos.write("org.apache.camel:camel-jms:${camel.version}".getBytes());
        }

        try (FileOutputStream fos = new FileOutputStream(new File(rootDir, "versions.properties"))) {
            fos.write("camel.version=4.18.2".getBytes());
        }
    }

    private String extractZipEntry(byte[] zipBytes, String fileName) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (fileName.equals(entry.getName())) {
                    return new String(zis.readAllBytes());
                }
                zis.closeEntry();
            }
        }
        throw new AssertionError("File '" + fileName + "' not found in ZIP");
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
