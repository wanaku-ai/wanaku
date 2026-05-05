package ai.wanaku.cli.main.commands.service;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import ai.wanaku.core.util.support.TavilyServiceCatalogHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TavilyServiceCatalogCLITest {

    @TempDir
    Path tempDir;

    @Test
    void testExposeGeneratesTavilySearchRules() throws Exception {
        setupTavilyCatalogDir();

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("tavily").toString());

        Integer result = cmd.call();
        assertEquals(0, result);

        String content = Files.readString(tempDir.resolve("tavily/" + TavilyServiceCatalogHelper.RULES_FILE));
        assertTrue(content.contains(TavilyServiceCatalogHelper.SERVICE_NAME), "Rules should contain the route ID");
    }

    @Test
    void testExposeWithNamespaceOnTavilyCatalog() throws Exception {
        setupTavilyCatalogDir();

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("tavily").toString());
        setField(cmd, "namespace", "production");

        Integer result = cmd.call();
        assertEquals(0, result);

        String content = Files.readString(tempDir.resolve("tavily/" + TavilyServiceCatalogHelper.RULES_FILE));
        assertTrue(content.contains(TavilyServiceCatalogHelper.SERVICE_NAME), "Rules should contain the route ID");
        assertTrue(content.contains("production"), "Rules should contain the namespace");
    }

    @Test
    void testExposeIgnoresStepLevelIdsInTavilyRoute() throws Exception {
        setupTavilyCatalogDirWithStepIds();

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("tavily").toString());

        Integer result = cmd.call();
        assertEquals(0, result);

        String content = Files.readString(tempDir.resolve("tavily/" + TavilyServiceCatalogHelper.RULES_FILE));
        assertTrue(content.contains(TavilyServiceCatalogHelper.SERVICE_NAME), "Should contain route-level ID");
        assertTrue(!content.contains("set-api-key-header"), "Should not contain step-level ID");
        assertTrue(!content.contains("call-search-engine"), "Should not contain step-level ID");
    }

    @Test
    void testPackageCreatesValidZipFromTavilyCatalog() throws Exception {
        setupTavilyCatalogDir();

        String outputPath = tempDir.resolve("tavily.b64").toString();

        ServicePackage cmd = new ServicePackage();
        setField(cmd, "path", tempDir.resolve("tavily").toString());
        setField(cmd, "output", outputPath);

        Integer result = cmd.call();
        assertEquals(0, result);

        File outputFile = new File(outputPath);
        assertTrue(outputFile.exists(), "Output file should exist");

        String base64Content = Files.readString(outputFile.toPath());
        byte[] zipBytes = java.util.Base64.getDecoder().decode(base64Content);

        boolean hasIndex = false;
        boolean hasCamelYaml = false;
        boolean hasRulesYaml = false;
        boolean hasDependencies = false;

        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("index.properties")) hasIndex = true;
                if (name.equals(TavilyServiceCatalogHelper.ROUTES_FILE)) hasCamelYaml = true;
                if (name.equals(TavilyServiceCatalogHelper.RULES_FILE)) hasRulesYaml = true;
                if (name.equals(TavilyServiceCatalogHelper.DEPENDENCIES_FILE)) hasDependencies = true;
            }
        }

        assertTrue(hasIndex, "ZIP should contain index.properties");
        assertTrue(hasCamelYaml, "ZIP should contain the Tavily route file");
        assertTrue(hasRulesYaml, "ZIP should contain the Tavily rules file");
        assertTrue(hasDependencies, "ZIP should contain the Tavily dependencies file");
    }

    @Test
    void testPackageIndexPropertiesContainsTavilyName() throws Exception {
        setupTavilyCatalogDir();

        String outputPath = tempDir.resolve("tavily.b64").toString();

        ServicePackage cmd = new ServicePackage();
        setField(cmd, "path", tempDir.resolve("tavily").toString());
        setField(cmd, "output", outputPath);

        Integer result = cmd.call();
        assertEquals(0, result);

        byte[] zipBytes = java.util.Base64.getDecoder().decode(Files.readString(new File(outputPath).toPath()));

        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("index.properties")) {
                    Properties props = new Properties();
                    props.load(zis);
                    assertEquals(TavilyServiceCatalogHelper.CATALOG_NAME, props.getProperty("catalog.name"));
                    assertEquals(TavilyServiceCatalogHelper.SERVICE_NAME, props.getProperty("catalog.services"));
                    break;
                }
            }
        }
    }

    @Test
    void testDeployFailsOnMissingTavilyRouteFile() throws Exception {
        File rootDir = tempDir.resolve("tavily-broken").toFile();
        rootDir.mkdirs();

        Properties props = new Properties();
        props.setProperty("catalog.name", TavilyServiceCatalogHelper.CATALOG_NAME);
        props.setProperty("catalog.description", TavilyServiceCatalogHelper.CATALOG_DESCRIPTION);
        props.setProperty("catalog.services", TavilyServiceCatalogHelper.SERVICE_NAME);
        props.setProperty(
                "catalog.routes." + TavilyServiceCatalogHelper.SERVICE_NAME, TavilyServiceCatalogHelper.ROUTES_FILE);
        props.setProperty(
                "catalog.rules." + TavilyServiceCatalogHelper.SERVICE_NAME, TavilyServiceCatalogHelper.RULES_FILE);
        props.setProperty(
                "catalog.dependencies." + TavilyServiceCatalogHelper.SERVICE_NAME,
                TavilyServiceCatalogHelper.DEPENDENCIES_FILE);

        File indexFile = new File(rootDir, "index.properties");
        try (FileWriter fw = new FileWriter(indexFile)) {
            props.store(fw, null);
        }

        File systemDir = new File(rootDir, "tavily-search");
        systemDir.mkdirs();
        new File(systemDir, TavilyServiceCatalogHelper.RULES_FILE_NAME).createNewFile();
        new File(systemDir, TavilyServiceCatalogHelper.DEPENDENCIES_FILE_NAME).createNewFile();
        // Deliberately NOT creating the route file

        ServiceDeploy cmd = new ServiceDeploy();
        setField(cmd, "path", rootDir.getAbsolutePath());
        setField(cmd, "host", "http://localhost:8080");

        Integer result = cmd.call();
        assertEquals(1, result);
    }

    private void setupTavilyCatalogDir() throws Exception {
        File rootDir = tempDir.resolve("tavily").toFile();
        TavilyServiceCatalogHelper.populateCatalogDirectory(rootDir.toPath());
    }

    private void setupTavilyCatalogDirWithStepIds() throws Exception {
        File rootDir = tempDir.resolve("tavily").toFile();
        rootDir.mkdirs();

        TavilyServiceCatalogHelper.populateCatalogDirectory(rootDir.toPath());
        File routeFile = new File(
                new File(rootDir, TavilyServiceCatalogHelper.SERVICE_NAME),
                TavilyServiceCatalogHelper.ROUTES_FILE_NAME);
        java.nio.file.Files.writeString(
                routeFile.toPath(),
                "- route:\n"
                        + "    id: tavily-search\n"
                        + "    description: Tavily web search\n"
                        + "    from:\n"
                        + "      uri: direct:tavily-search\n"
                        + "    steps:\n"
                        + "      - set-header:\n"
                        + "          id: set-api-key-header\n"
                        + "          name: apiKey\n"
                        + "          simple: \"{{tavily.api.key}}\"\n"
                        + "      - to:\n"
                        + "          id: call-search-engine\n"
                        + "          uri: langchain4j-web-search:wanakuTavily\n");
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
