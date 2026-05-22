package ai.wanaku.core.services.api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCatalogIndexTest {

    private final String TEST_SERVICE_NAME = "testservice";
    private final String TEST_SERVICE_DESC = "A test service";
    private final String TEST_SYSTEM = "sys1";
    private final String INDEX_PROPERTIES_PATH = "index.properties";

    @Test
    void testValidIndex() throws Exception {
        byte[] zip = createTestZip(TEST_SERVICE_NAME, TEST_SERVICE_DESC, TEST_SYSTEM);
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        assertEquals(TEST_SERVICE_NAME, index.getName());
        assertEquals(TEST_SERVICE_DESC, index.getDescription());
        assertEquals(1, index.getServiceNames().size());
        assertEquals(TEST_SYSTEM, index.getServiceNames().get(0));
        assertEquals(TEST_SYSTEM + "/" + TEST_SYSTEM + ".camel.yaml", index.getRoutesFile(TEST_SYSTEM));
        assertEquals(TEST_SYSTEM + "/" + TEST_SYSTEM + ".wanaku-rules.yaml", index.getRulesFile(TEST_SYSTEM));
        assertNull(index.getDependenciesFile(TEST_SYSTEM));
    }

    @Test
    void testValidIndexWithIcon() throws Exception {
        byte[] zip = createTestZipWithIcon(TEST_SERVICE_NAME, "icon-test", TEST_SERVICE_DESC, TEST_SYSTEM);
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        assertEquals("icon-test", index.getIcon());
    }

    @Test
    void testMissingIndexProperties() {
        byte[] zip = createZipWithoutIndex();
        WanakuException ex = assertThrows(WanakuException.class, () -> ServiceCatalogIndex.fromZipBytes(zip));
        assertTrue(ex.getMessage().contains(INDEX_PROPERTIES_PATH));
    }

    @Test
    void testMissingCatalogName() {
        byte[] zip = createZipWithProperties("", "desc", "sys1");
        WanakuException ex = assertThrows(WanakuException.class, () -> ServiceCatalogIndex.fromZipBytes(zip));
        assertTrue(ex.getMessage().contains("catalog.name"));
    }

    @Test
    void testMissingRoutesFileInZip() {
        byte[] zip = createZipMissingRouteFile("test", "desc", "sys1");
        WanakuException ex = assertThrows(WanakuException.class, () -> ServiceCatalogIndex.fromZipBytes(zip));
        assertTrue(ex.getMessage().contains("not found in ZIP"));
    }

    @Test
    void testPathTraversalRejection() {
        assertThrows(WanakuException.class, () -> ServiceCatalogIndex.validateZipEntryPath("../etc/passwd"));
    }

    @Test
    void testAbsolutePathRejection() {
        assertThrows(WanakuException.class, () -> ServiceCatalogIndex.validateZipEntryPath("/etc/passwd"));
    }

    @Test
    void testMultiSystemCatalog() {
        byte[] zip = createTestZip("finance", "Finance service", "finance-new", "finance-legacy");
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        assertEquals("finance", index.getName());
        assertEquals(2, index.getServiceNames().size());
        assertTrue(index.getServiceNames().contains("finance-new"));
        assertTrue(index.getServiceNames().contains("finance-legacy"));
        assertNotNull(index.getRoutesFile("finance-new"));
        assertNotNull(index.getRoutesFile("finance-legacy"));
    }

    @Test
    void testFromBase64() {
        byte[] zip = createTestZip("b64test", "Base64 test", "sys1");
        String base64 = Base64.getEncoder().encodeToString(zip);
        ServiceCatalogIndex index = ServiceCatalogIndex.fromBase64(base64);

        assertEquals("b64test", index.getName());
    }

    @Test
    void testInvalidBase64() {
        assertThrows(WanakuException.class, () -> ServiceCatalogIndex.fromBase64("not-valid-base64!!!"));
    }

    @Test
    void testEmptyServicesProperty() {
        byte[] zip = createZipWithEmptyServices();
        WanakuException ex = assertThrows(WanakuException.class, () -> ServiceCatalogIndex.fromZipBytes(zip));
        assertTrue(ex.getMessage().contains("catalog.services"));
    }

    @Test
    void testVersionsFileReference() throws Exception {
        byte[] zip = createTestZipWithVersions("vtest", "Versions test", "sys1");
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        assertTrue(index.hasVersionsFile());
        assertEquals("versions.properties", index.getVersionsFile());
    }

    @Test
    void testNoVersionsFileByDefault() throws Exception {
        byte[] zip = createTestZip("testservice", "A test service", "sys1");
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        assertFalse(index.hasVersionsFile());
        assertNull(index.getVersionsFile());
    }

    @Test
    void testMissingVersionsFileInZip() {
        byte[] zip = createTestZipWithMissingVersions("test", "desc", "sys1");
        WanakuException ex = assertThrows(WanakuException.class, () -> ServiceCatalogIndex.fromZipBytes(zip));
        assertTrue(ex.getMessage().contains("not found in ZIP"));
    }

    @Test
    void testResolvePlaceholders() throws Exception {
        byte[] zip = createTestZipWithVersionsAndDeps("resolve-test", "Resolve test", "sys1");
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        byte[] resolved = index.resolvePlaceholders(zip);

        String depsContent = extractFileFromZip(resolved, "sys1/sys1.dependencies.txt");
        assertTrue(depsContent.contains("com.example:lib-a:1.0.0"));
        assertFalse(depsContent.contains("${ver.a}"));
    }

    @Test
    void testResolvePlaceholdersNoVersionsFile() throws Exception {
        byte[] zip = createTestZipWithDeps("no-versions", "No versions test", "sys1");
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        byte[] resolved = index.resolvePlaceholders(zip);
        // Should return same bytes when no versions file present
        assertEquals(zip, resolved);
    }

    @Test
    void testResolvePlaceholdersPreservesVersionsFile() throws Exception {
        byte[] zip = createTestZipWithVersionsAndDeps("preserve-test", "Preserve test", "sys1");
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        byte[] resolved = index.resolvePlaceholders(zip);

        String versionsContent = extractFileFromZip(resolved, "versions.properties");
        assertTrue(versionsContent.contains("ver.a="));
    }

    @Test
    void testResolvePlaceholdersLeavesUnresolvedPlaceholders() throws Exception {
        byte[] zip = createTestZipWithVersionsAndDepsPartial("partial-test", "Partial test", "sys1");
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        byte[] resolved = index.resolvePlaceholders(zip);

        String depsContent = extractFileFromZip(resolved, "sys1/sys1.dependencies.txt");
        assertTrue(depsContent.contains("com.example:lib-a:1.0.0"));
        assertTrue(depsContent.contains("${unknown.version}"));
    }

    // Helper methods to create test ZIPs

    private byte[] createTestZip(String name, String description, String... systems) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // Create index.properties
                Properties props = new Properties();
                props.setProperty("catalog.name", name);
                props.setProperty("catalog.description", description);
                props.setProperty("catalog.services", String.join(",", systems));

                for (String sys : systems) {
                    String routesPath = sys + "/" + sys + ".camel.yaml";
                    String rulesPath = sys + "/" + sys + ".wanaku-rules.yaml";
                    props.setProperty("catalog.routes." + sys, routesPath);
                    props.setProperty("catalog.rules." + sys, rulesPath);

                    // Add the actual files
                    zos.putNextEntry(new ZipEntry(routesPath));
                    zos.write(("# Routes for " + sys).getBytes());
                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry(rulesPath));
                    zos.write(("# Rules for " + sys).getBytes());
                    zos.closeEntry();
                }

                // Write index.properties
                zos.putNextEntry(new ZipEntry(INDEX_PROPERTIES_PATH));
                ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
                props.store(propsOut, null);
                zos.write(propsOut.toByteArray());
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createTestZipWithIcon(String name, String icon, String description, String... systems) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                Properties props = new Properties();
                props.setProperty("catalog.name", name);
                props.setProperty("catalog.icon", icon);
                props.setProperty("catalog.description", description);
                props.setProperty("catalog.services", String.join(",", systems));

                for (String sys : systems) {
                    String routesPath = sys + "/" + sys + ".camel.yaml";
                    String rulesPath = sys + "/" + sys + ".wanaku-rules.yaml";
                    props.setProperty("catalog.routes." + sys, routesPath);
                    props.setProperty("catalog.rules." + sys, rulesPath);

                    zos.putNextEntry(new ZipEntry(routesPath));
                    zos.write(("# Routes for " + sys).getBytes());
                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry(rulesPath));
                    zos.write(("# Rules for " + sys).getBytes());
                    zos.closeEntry();
                }

                zos.putNextEntry(new ZipEntry(INDEX_PROPERTIES_PATH));
                ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
                props.store(propsOut, null);
                zos.write(propsOut.toByteArray());
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createZipWithoutIndex() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("some-file.txt"));
                zos.write("data".getBytes());
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createZipWithProperties(String name, String description, String system) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                Properties props = new Properties();
                if (!name.isEmpty()) {
                    props.setProperty("catalog.name", name);
                }
                props.setProperty("catalog.description", description);
                props.setProperty("catalog.services", system);
                props.setProperty("catalog.routes." + system, system + "/" + system + ".camel.yaml");
                props.setProperty("catalog.rules." + system, system + "/" + system + ".wanaku-rules.yaml");

                zos.putNextEntry(new ZipEntry(INDEX_PROPERTIES_PATH));
                ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
                props.store(propsOut, null);
                zos.write(propsOut.toByteArray());
                zos.closeEntry();

                // Add referenced files
                zos.putNextEntry(new ZipEntry(system + "/" + system + ".camel.yaml"));
                zos.write("routes".getBytes());
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry(system + "/" + system + ".wanaku-rules.yaml"));
                zos.write("rules".getBytes());
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createZipMissingRouteFile(String name, String description, String system) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                Properties props = new Properties();
                props.setProperty("catalog.name", name);
                props.setProperty("catalog.description", description);
                props.setProperty("catalog.services", system);
                props.setProperty("catalog.routes." + system, system + "/" + system + ".camel.yaml");
                props.setProperty("catalog.rules." + system, system + "/" + system + ".wanaku-rules.yaml");

                zos.putNextEntry(new ZipEntry(INDEX_PROPERTIES_PATH));
                ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
                props.store(propsOut, null);
                zos.write(propsOut.toByteArray());
                zos.closeEntry();

                // Only add rules file, NOT routes file
                zos.putNextEntry(new ZipEntry(system + "/" + system + ".wanaku-rules.yaml"));
                zos.write("rules".getBytes());
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createZipWithEmptyServices() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                Properties props = new Properties();
                props.setProperty("catalog.name", "test");
                props.setProperty("catalog.description", "test");
                props.setProperty("catalog.services", "");

                zos.putNextEntry(new ZipEntry(INDEX_PROPERTIES_PATH));
                ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
                props.store(propsOut, null);
                zos.write(propsOut.toByteArray());
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createTestZipWithVersions(String name, String description, String... systems) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                Properties props = new Properties();
                props.setProperty("catalog.name", name);
                props.setProperty("catalog.description", description);
                props.setProperty("catalog.services", String.join(",", systems));
                props.setProperty("catalog.versions", "versions.properties");

                for (String sys : systems) {
                    String routesPath = sys + "/" + sys + ".camel.yaml";
                    String rulesPath = sys + "/" + sys + ".wanaku-rules.yaml";
                    props.setProperty("catalog.routes." + sys, routesPath);
                    props.setProperty("catalog.rules." + sys, rulesPath);

                    zos.putNextEntry(new ZipEntry(routesPath));
                    zos.write(("# Routes for " + sys).getBytes());
                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry(rulesPath));
                    zos.write(("# Rules for " + sys).getBytes());
                    zos.closeEntry();
                }

                zos.putNextEntry(new ZipEntry("versions.properties"));
                zos.write("ver.a=1.0.0\n".getBytes());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry(INDEX_PROPERTIES_PATH));
                ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
                props.store(propsOut, null);
                zos.write(propsOut.toByteArray());
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createTestZipWithVersionsAndDeps(String name, String description, String... systems) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                Properties props = new Properties();
                props.setProperty("catalog.name", name);
                props.setProperty("catalog.description", description);
                props.setProperty("catalog.services", String.join(",", systems));
                props.setProperty("catalog.versions", "versions.properties");

                for (String sys : systems) {
                    String routesPath = sys + "/" + sys + ".camel.yaml";
                    String rulesPath = sys + "/" + sys + ".wanaku-rules.yaml";
                    String depsPath = sys + "/" + sys + ".dependencies.txt";
                    props.setProperty("catalog.routes." + sys, routesPath);
                    props.setProperty("catalog.rules." + sys, rulesPath);
                    props.setProperty("catalog.dependencies." + sys, depsPath);

                    zos.putNextEntry(new ZipEntry(routesPath));
                    zos.write(("# Routes for " + sys).getBytes());
                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry(rulesPath));
                    zos.write(("# Rules for " + sys).getBytes());
                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry(depsPath));
                    zos.write(("com.example:lib-a:${ver.a}\n").getBytes());
                    zos.closeEntry();
                }

                zos.putNextEntry(new ZipEntry("versions.properties"));
                zos.write("ver.a=1.0.0\n".getBytes());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry(INDEX_PROPERTIES_PATH));
                ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
                props.store(propsOut, null);
                zos.write(propsOut.toByteArray());
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createTestZipWithVersionsAndDepsPartial(String name, String description, String... systems) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                Properties props = new Properties();
                props.setProperty("catalog.name", name);
                props.setProperty("catalog.description", description);
                props.setProperty("catalog.services", String.join(",", systems));
                props.setProperty("catalog.versions", "versions.properties");

                for (String sys : systems) {
                    String routesPath = sys + "/" + sys + ".camel.yaml";
                    String rulesPath = sys + "/" + sys + ".wanaku-rules.yaml";
                    String depsPath = sys + "/" + sys + ".dependencies.txt";
                    props.setProperty("catalog.routes." + sys, routesPath);
                    props.setProperty("catalog.rules." + sys, rulesPath);
                    props.setProperty("catalog.dependencies." + sys, depsPath);

                    zos.putNextEntry(new ZipEntry(routesPath));
                    zos.write(("# Routes for " + sys).getBytes());
                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry(rulesPath));
                    zos.write(("# Rules for " + sys).getBytes());
                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry(depsPath));
                    zos.write(("com.example:lib-a:${ver.a}\ncom.example:lib-b:${unknown.version}\n").getBytes());
                    zos.closeEntry();
                }

                zos.putNextEntry(new ZipEntry("versions.properties"));
                zos.write("ver.a=1.0.0\n".getBytes());
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry(INDEX_PROPERTIES_PATH));
                ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
                props.store(propsOut, null);
                zos.write(propsOut.toByteArray());
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createTestZipWithDeps(String name, String description, String... systems) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                Properties props = new Properties();
                props.setProperty("catalog.name", name);
                props.setProperty("catalog.description", description);
                props.setProperty("catalog.services", String.join(",", systems));

                for (String sys : systems) {
                    String routesPath = sys + "/" + sys + ".camel.yaml";
                    String rulesPath = sys + "/" + sys + ".wanaku-rules.yaml";
                    String depsPath = sys + "/" + sys + ".dependencies.txt";
                    props.setProperty("catalog.routes." + sys, routesPath);
                    props.setProperty("catalog.rules." + sys, rulesPath);
                    props.setProperty("catalog.dependencies." + sys, depsPath);

                    zos.putNextEntry(new ZipEntry(routesPath));
                    zos.write(("# Routes for " + sys).getBytes());
                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry(rulesPath));
                    zos.write(("# Rules for " + sys).getBytes());
                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry(depsPath));
                    zos.write(("com.example:lib-a:${ver.a}\n").getBytes());
                    zos.closeEntry();
                }

                zos.putNextEntry(new ZipEntry(INDEX_PROPERTIES_PATH));
                ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
                props.store(propsOut, null);
                zos.write(propsOut.toByteArray());
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createTestZipWithMissingVersions(String name, String description, String... systems) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                Properties props = new Properties();
                props.setProperty("catalog.name", name);
                props.setProperty("catalog.description", description);
                props.setProperty("catalog.services", String.join(",", systems));
                props.setProperty("catalog.versions", "versions.properties");

                for (String sys : systems) {
                    String routesPath = sys + "/" + sys + ".camel.yaml";
                    String rulesPath = sys + "/" + sys + ".wanaku-rules.yaml";
                    props.setProperty("catalog.routes." + sys, routesPath);
                    props.setProperty("catalog.rules." + sys, rulesPath);

                    zos.putNextEntry(new ZipEntry(routesPath));
                    zos.write(("# Routes for " + sys).getBytes());
                    zos.closeEntry();

                    zos.putNextEntry(new ZipEntry(rulesPath));
                    zos.write(("# Rules for " + sys).getBytes());
                    zos.closeEntry();
                }

                // Deliberately NOT adding versions.properties file

                zos.putNextEntry(new ZipEntry(INDEX_PROPERTIES_PATH));
                ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
                props.store(propsOut, null);
                zos.write(propsOut.toByteArray());
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String extractFileFromZip(byte[] zipBytes, String fileName) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
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
}
