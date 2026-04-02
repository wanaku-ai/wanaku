package ai.wanaku.cli.main.commands.service;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceDeployTest {

    @TempDir
    Path tempDir;

    @Test
    void testDeployFailsWithMissingIndex() throws Exception {
        tempDir.resolve("empty").toFile().mkdirs();

        ServiceDeploy cmd = new ServiceDeploy();
        setField(cmd, "path", tempDir.resolve("empty").toString());
        setField(cmd, "host", "http://localhost:8080");

        Integer result = cmd.call();
        assertEquals(1, result);
    }

    @Test
    void testDeployFailsWithMissingCatalogName() throws Exception {
        File rootDir = tempDir.resolve("noname").toFile();
        rootDir.mkdirs();

        Properties props = new Properties();
        // Missing catalog.name
        props.setProperty("catalog.description", "test");
        props.setProperty("catalog.services", "sys1");

        File indexFile = new File(rootDir, "index.properties");
        try (FileWriter fw = new FileWriter(indexFile)) {
            props.store(fw, null);
        }

        ServiceDeploy cmd = new ServiceDeploy();
        setField(cmd, "path", rootDir.getAbsolutePath());
        setField(cmd, "host", "http://localhost:8080");

        Integer result = cmd.call();
        assertEquals(1, result);
    }

    @Test
    void testDeployFailsWithMissingRouteFile() throws Exception {
        File rootDir = tempDir.resolve("missingroute").toFile();
        rootDir.mkdirs();

        Properties props = new Properties();
        props.setProperty("catalog.name", "test");
        props.setProperty("catalog.description", "test");
        props.setProperty("catalog.services", "sys1");
        props.setProperty("catalog.routes.sys1", "sys1/sys1.camel.yaml");
        props.setProperty("catalog.rules.sys1", "sys1/sys1.wanaku-rules.yaml");

        File indexFile = new File(rootDir, "index.properties");
        try (FileWriter fw = new FileWriter(indexFile)) {
            props.store(fw, null);
        }

        // Do NOT create the route file

        ServiceDeploy cmd = new ServiceDeploy();
        setField(cmd, "path", rootDir.getAbsolutePath());
        setField(cmd, "host", "http://localhost:8080");

        Integer result = cmd.call();
        assertEquals(1, result);
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
