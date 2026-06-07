package ai.wanaku.cli.main.commands.service;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Properties;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.support.WanakuPrinter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ServiceInitTest {

    @TempDir
    Path tempDir;

    @Mock
    private Terminal terminal;

    @Mock
    private WanakuPrinter printer;

    @Test
    void testInitCreatesSingleSystem() throws Exception {
        ServiceInit cmd = new ServiceInit();
        setField(cmd, "name", tempDir.resolve("testservice").toAbsolutePath().toString());
        setField(cmd, "services", "sys1");

        Integer result = cmd.doCall(terminal, printer);
        assertEquals(0, result);

        File rootDir = tempDir.resolve("testservice").toFile();
        assertTrue(rootDir.exists(), "Root directory should exist");
        assertTrue(new File(rootDir, "index.properties").exists(), "index.properties should exist");
        assertTrue(new File(rootDir, "sys1/sys1.camel.yaml").exists(), "Route file should exist");
        assertTrue(new File(rootDir, "sys1/sys1.wanaku-rules.yaml").exists(), "Rules file should exist");
        assertTrue(new File(rootDir, "sys1/sys1.dependencies.txt").exists(), "Dependencies file should exist");

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
        setField(cmd, "name", tempDir.resolve("multiservice").toAbsolutePath().toString());
        setField(cmd, "services", "sys1,sys2,sys3");

        Integer result = cmd.doCall(terminal, printer);
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
        File existing = tempDir.resolve("existing").toFile();
        existing.mkdirs();

        ServiceInit cmd = new ServiceInit();
        setField(cmd, "name", existing.getAbsolutePath());
        setField(cmd, "services", "sys1");

        Integer result = cmd.doCall(terminal, printer);
        assertEquals(1, result);
    }

    @Test
    void testInitIndexPropertiesHasCorrectServicesList() throws Exception {
        ServiceInit cmd = new ServiceInit();
        setField(cmd, "name", tempDir.resolve("proptest").toAbsolutePath().toString());
        setField(cmd, "services", "alpha,beta");

        cmd.doCall(terminal, printer);

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
