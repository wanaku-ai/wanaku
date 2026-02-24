package ai.wanaku.core.services.api;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCatalogIndexTest {

    @Test
    void testValidIndex() throws Exception {
        byte[] zip = createTestZip("testservice", "A test service", "sys1");
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        assertEquals("testservice", index.getName());
        assertEquals("A test service", index.getDescription());
        assertEquals(1, index.getServiceNames().size());
        assertEquals("sys1", index.getServiceNames().get(0));
        assertEquals("sys1/sys1.camel.yaml", index.getRoutesFile("sys1"));
        assertEquals("sys1/sys1.wanaku-rules.yaml", index.getRulesFile("sys1"));
        assertNull(index.getDependenciesFile("sys1"));
    }

    @Test
    void testValidIndexWithIcon() throws Exception {
        byte[] zip = createTestZipWithIcon("testservice", "icon-test", "A test service", "sys1");
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        assertEquals("icon-test", index.getIcon());
    }

    @Test
    void testMissingIndexProperties() {
        byte[] zip = createZipWithoutIndex();
        WanakuException ex = assertThrows(WanakuException.class, () -> ServiceCatalogIndex.fromZipBytes(zip));
        assertTrue(ex.getMessage().contains("index.properties"));
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
    void testMultiSystemCatalog() throws Exception {
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
    void testFromBase64() throws Exception {
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
                zos.putNextEntry(new ZipEntry("index.properties"));
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

                zos.putNextEntry(new ZipEntry("index.properties"));
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

                zos.putNextEntry(new ZipEntry("index.properties"));
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

                zos.putNextEntry(new ZipEntry("index.properties"));
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

                zos.putNextEntry(new ZipEntry("index.properties"));
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
}
