package ai.wanaku.cli.main.commands.service;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceExposeTest {

    @TempDir
    Path tempDir;

    @Test
    void testExposeGeneratesRules() throws Exception {
        // Set up a service catalog directory
        setupServiceDir("myservice", "sys1");

        // Write a Camel route with an ID
        File routeFile = tempDir.resolve("myservice/sys1/sys1.camel.yaml").toFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(routeFile))) {
            pw.println("- route:");
            pw.println("    id: my-route-1");
            pw.println("    from:");
            pw.println("      uri: \"direct:start\"");
        }

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("myservice").toString());

        Integer result = cmd.call();
        assertEquals(0, result);

        // Verify rules file was generated
        File rulesFile =
                tempDir.resolve("myservice/sys1/sys1.wanaku-rules.yaml").toFile();
        assertTrue(rulesFile.exists());
        String content = Files.readString(rulesFile.toPath());
        assertTrue(content.contains("my-route-1"));
    }

    @Test
    void testExposeWithNamespace() throws Exception {
        setupServiceDir("nsservice", "sys1");

        File routeFile = tempDir.resolve("nsservice/sys1/sys1.camel.yaml").toFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(routeFile))) {
            pw.println("- route:");
            pw.println("    id: ns-route");
            pw.println("    from:");
            pw.println("      uri: \"direct:start\"");
        }

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("nsservice").toString());
        setField(cmd, "namespace", "production");

        Integer result = cmd.call();
        assertEquals(0, result);

        String content = Files.readString(tempDir.resolve("nsservice/sys1/sys1.wanaku-rules.yaml"));
        assertTrue(content.contains("production"));
    }

    @Test
    void testExposeMissingIndexProperties() throws Exception {
        // Directory without index.properties
        tempDir.resolve("empty").toFile().mkdirs();

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("empty").toString());

        Integer result = cmd.call();
        assertEquals(1, result);
    }

    @Test
    void testExposeMultipleRouteIds() throws Exception {
        setupServiceDir("multiroute", "sys1");

        File routeFile = tempDir.resolve("multiroute/sys1/sys1.camel.yaml").toFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(routeFile))) {
            pw.println("- route:");
            pw.println("    id: route-alpha");
            pw.println("    from:");
            pw.println("      uri: \"direct:alpha\"");
            pw.println("- route:");
            pw.println("    id: route-beta");
            pw.println("    from:");
            pw.println("      uri: \"direct:beta\"");
        }

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("multiroute").toString());

        Integer result = cmd.call();
        assertEquals(0, result);

        String content = Files.readString(tempDir.resolve("multiroute/sys1/sys1.wanaku-rules.yaml"));
        assertTrue(content.contains("route-alpha"));
        assertTrue(content.contains("route-beta"));
    }

    private void setupServiceDir(String name, String... systems) throws Exception {
        File rootDir = tempDir.resolve(name).toFile();
        rootDir.mkdirs();

        Properties props = new Properties();
        props.setProperty("catalog.name", name);
        props.setProperty("catalog.description", "Test service " + name);
        props.setProperty("catalog.services", String.join(",", systems));

        for (String sys : systems) {
            File sysDir = new File(rootDir, sys);
            sysDir.mkdirs();

            props.setProperty("catalog.routes." + sys, sys + "/" + sys + ".camel.yaml");
            props.setProperty("catalog.rules." + sys, sys + "/" + sys + ".wanaku-rules.yaml");

            // Create empty route file
            new File(sysDir, sys + ".camel.yaml").createNewFile();
            new File(sysDir, sys + ".wanaku-rules.yaml").createNewFile();
        }

        File indexFile = new File(rootDir, "index.properties");
        try (FileWriter fw = new FileWriter(indexFile)) {
            props.store(fw, null);
        }
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
