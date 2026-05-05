package ai.wanaku.core.services.api;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.core.util.support.TavilyServiceCatalogHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavilyServiceCatalogIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void testParseTavilyCatalogFromZipBytes() throws Exception {
        byte[] zip = TavilyServiceCatalogHelper.createCatalogZip();
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        assertEquals(TavilyServiceCatalogHelper.CATALOG_NAME, index.getName());
        assertNotNull(index.getIcon());
        assertEquals(TavilyServiceCatalogHelper.CATALOG_DESCRIPTION, index.getDescription());
        assertEquals(1, index.getServiceNames().size());
        assertEquals(
                TavilyServiceCatalogHelper.SERVICE_NAME, index.getServiceNames().get(0));
        assertEquals(
                TavilyServiceCatalogHelper.ROUTES_FILE, index.getRoutesFile(TavilyServiceCatalogHelper.SERVICE_NAME));
        assertEquals(
                TavilyServiceCatalogHelper.RULES_FILE, index.getRulesFile(TavilyServiceCatalogHelper.SERVICE_NAME));
        assertEquals(
                TavilyServiceCatalogHelper.DEPENDENCIES_FILE,
                index.getDependenciesFile(TavilyServiceCatalogHelper.SERVICE_NAME));
    }

    @Test
    void testParseTavilyCatalogFromBase64() throws Exception {
        String base64 = TavilyServiceCatalogHelper.createCatalogBase64();
        ServiceCatalogIndex index = ServiceCatalogIndex.fromBase64(base64);

        assertEquals(TavilyServiceCatalogHelper.CATALOG_NAME, index.getName());
        assertEquals(
                TavilyServiceCatalogHelper.SERVICE_NAME, index.getServiceNames().get(0));
    }

    @Test
    void testTavilyCatalogZipFromDisk() throws Exception {
        Path catalogDir = tempDir.resolve("tavily");
        TavilyServiceCatalogHelper.populateCatalogDirectory(catalogDir);
        byte[] zip = zipDirectory(catalogDir);
        ServiceCatalogIndex index = ServiceCatalogIndex.fromZipBytes(zip);

        assertEquals(TavilyServiceCatalogHelper.CATALOG_NAME, index.getName());
        assertEquals(1, index.getServiceNames().size());
        assertEquals(
                TavilyServiceCatalogHelper.SERVICE_NAME, index.getServiceNames().get(0));
        assertNotNull(index.getDependenciesFile(TavilyServiceCatalogHelper.SERVICE_NAME));
    }

    @Test
    void testTavilyCatalogMissingDependenciesFileInZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Properties props = new Properties();
            props.setProperty("catalog.name", TavilyServiceCatalogHelper.CATALOG_NAME);
            props.setProperty("catalog.description", TavilyServiceCatalogHelper.CATALOG_DESCRIPTION);
            props.setProperty("catalog.services", TavilyServiceCatalogHelper.SERVICE_NAME);
            props.setProperty(
                    "catalog.routes." + TavilyServiceCatalogHelper.SERVICE_NAME,
                    TavilyServiceCatalogHelper.ROUTES_FILE);
            props.setProperty(
                    "catalog.rules." + TavilyServiceCatalogHelper.SERVICE_NAME, TavilyServiceCatalogHelper.RULES_FILE);
            props.setProperty(
                    "catalog.dependencies." + TavilyServiceCatalogHelper.SERVICE_NAME,
                    TavilyServiceCatalogHelper.DEPENDENCIES_FILE);

            zos.putNextEntry(new ZipEntry("index.properties"));
            ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
            props.store(propsOut, null);
            zos.write(propsOut.toByteArray());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(TavilyServiceCatalogHelper.ROUTES_FILE));
            zos.write("- route:\n    id: tavily-search\n".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(TavilyServiceCatalogHelper.RULES_FILE));
            zos.write("mcp:\n  tools: []\n".getBytes());
            zos.closeEntry();

            // Deliberately NOT adding the dependencies file
        }

        WanakuException ex =
                assertThrows(WanakuException.class, () -> ServiceCatalogIndex.fromZipBytes(baos.toByteArray()));
        assertTrue(ex.getMessage().contains("not found in ZIP"));
    }

    @Test
    void testTavilyCatalogMissingRoutesFileInZip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Properties props = new Properties();
            props.setProperty("catalog.name", TavilyServiceCatalogHelper.CATALOG_NAME);
            props.setProperty("catalog.description", TavilyServiceCatalogHelper.CATALOG_DESCRIPTION);
            props.setProperty("catalog.services", TavilyServiceCatalogHelper.SERVICE_NAME);
            props.setProperty(
                    "catalog.routes." + TavilyServiceCatalogHelper.SERVICE_NAME,
                    TavilyServiceCatalogHelper.ROUTES_FILE);
            props.setProperty(
                    "catalog.rules." + TavilyServiceCatalogHelper.SERVICE_NAME, TavilyServiceCatalogHelper.RULES_FILE);

            zos.putNextEntry(new ZipEntry("index.properties"));
            ByteArrayOutputStream propsOut = new ByteArrayOutputStream();
            props.store(propsOut, null);
            zos.write(propsOut.toByteArray());
            zos.closeEntry();

            // Only add rules, not routes
            zos.putNextEntry(new ZipEntry(TavilyServiceCatalogHelper.RULES_FILE));
            zos.write("mcp:\n  tools: []\n".getBytes());
            zos.closeEntry();
        }

        WanakuException ex =
                assertThrows(WanakuException.class, () -> ServiceCatalogIndex.fromZipBytes(baos.toByteArray()));
        assertTrue(ex.getMessage().contains("not found in ZIP"));
    }

    private byte[] zipDirectory(Path dir) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                addDirToZip(zos, dir.toFile(), "");
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void addDirToZip(ZipOutputStream zos, File dir, String prefix) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String entryName = prefix.isEmpty() ? file.getName() : prefix + "/" + file.getName();
            if (file.isDirectory()) {
                addDirToZip(zos, file, entryName);
            } else {
                zos.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
    }
}
