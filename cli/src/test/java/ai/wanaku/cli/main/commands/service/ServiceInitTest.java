package ai.wanaku.cli.main.commands.service;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceInitTest {

    @TempDir
    Path tempDir;

    private Path originalDir;

    @BeforeEach
    void setUp() {
        originalDir = Path.of(System.getProperty("user.dir"));
        // Change working directory for the test (since ServiceInit creates relative dirs)
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalDir.toString());
    }

    @Test
    void testInitCreatesSingleSystem() throws Exception {
        ServiceInit cmd = new ServiceInit();
        setField(cmd, "name", tempDir.resolve("testservice").toString());
        setField(cmd, "services", "sys1");

        Integer result = cmd.call();
        assertEquals(0, result);

        File rootDir = tempDir.resolve("testservice").toFile();
        assertTrue(rootDir.exists(), "Root directory should exist");
        assertTrue(new File(rootDir, "index.properties").exists(), "index.properties should exist");
        assertTrue(new File(rootDir, "sys1/sys1.camel.yaml").exists(), "Route file should exist");
        assertTrue(new File(rootDir, "sys1/sys1.wanaku-rules.yaml").exists(), "Rules file should exist");
        assertTrue(new File(rootDir, "sys1/sys1.dependencies.txt").exists(), "Dependencies file should exist");

        // Verify index.properties content
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(new File(rootDir, "index.properties"))) {
            props.load(fis);
        }
        assertNotNull(props.getProperty("catalog.name"));
        assertNotNull(props.getProperty("catalog.services"));
        assertNotNull(props.getProperty("catalog.routes.sys1"));
        assertNotNull(props.getProperty("catalog.rules.sys1"));
    }

    @Test
    void testInitCreatesMultipleSystems() throws Exception {
        ServiceInit cmd = new ServiceInit();
        setField(cmd, "name", tempDir.resolve("multiservice").toString());
        setField(cmd, "services", "sys1,sys2,sys3");

        Integer result = cmd.call();
        assertEquals(0, result);

        File rootDir = tempDir.resolve("multiservice").toFile();
        assertTrue(rootDir.exists());

        for (String sys : new String[] {"sys1", "sys2", "sys3"}) {
            assertTrue(
                    new File(rootDir, sys + "/" + sys + ".camel.yaml").exists(),
                    "Route file for " + sys + " should exist");
            assertTrue(
                    new File(rootDir, sys + "/" + sys + ".wanaku-rules.yaml").exists(),
                    "Rules file for " + sys + " should exist");
        }
    }

    @Test
    void testInitFailsWhenDirectoryExists() throws Exception {
        // Pre-create the directory
        File existing = tempDir.resolve("existing").toFile();
        existing.mkdirs();

        ServiceInit cmd = new ServiceInit();
        setField(cmd, "name", existing.getAbsolutePath());
        setField(cmd, "services", "sys1");

        Integer result = cmd.call();
        assertEquals(1, result);
    }

    @Test
    void testInitIndexPropertiesHasCorrectServicesList() throws Exception {
        ServiceInit cmd = new ServiceInit();
        setField(cmd, "name", tempDir.resolve("proptest").toString());
        setField(cmd, "services", "alpha,beta");

        cmd.call();

        Properties props = new Properties();
        try (FileInputStream fis =
                new FileInputStream(tempDir.resolve("proptest/index.properties").toFile())) {
            props.load(fis);
        }

        String services = props.getProperty("catalog.services");
        assertTrue(services.contains("alpha"));
        assertTrue(services.contains("beta"));
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
