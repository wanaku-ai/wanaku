package ai.wanaku.cli.main.commands.service;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.support.NamespaceOptions;
import ai.wanaku.cli.main.support.WanakuPrinter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ServiceExposeTest {

    @TempDir
    Path tempDir;

    @Mock
    private Terminal terminal;

    @Mock
    private WanakuPrinter printer;

    @Test
    void testExposeGeneratesRules() throws Exception {
        setupServiceDir("myservice", "sys1");

        File routeFile = tempDir.resolve("myservice/sys1/sys1.camel.yaml").toFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(routeFile, StandardCharsets.UTF_8))) {
            pw.println("- route:");
            pw.println("    id: my-route-1");
            pw.println("    from:");
            pw.println("      uri: \"direct:start\"");
        }

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("myservice").toAbsolutePath().toString());

        Integer result = cmd.doCall(terminal, printer);
        assertEquals(0, result);

        File rulesFile =
                tempDir.resolve("myservice/sys1/sys1.wanaku-rules.yaml").toFile();
        assertTrue(rulesFile.exists());
        String content = Files.readString(rulesFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("my-route-1"));
        assertTrue(content.contains("wanaku_body"), "Tools should include wanaku_body property by default");
    }

    @Test
    void testExposeWithNamespace() throws Exception {
        setupServiceDir("nsservice", "sys1");

        File routeFile = tempDir.resolve("nsservice/sys1/sys1.camel.yaml").toFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(routeFile, StandardCharsets.UTF_8))) {
            pw.println("- route:");
            pw.println("    id: ns-route");
            pw.println("    from:");
            pw.println("      uri: \"direct:start\"");
        }

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("nsservice").toAbsolutePath().toString());
        setField(cmd, "namespaceOptions", createNamespaceOptions("production"));

        Integer result = cmd.doCall(terminal, printer);
        assertEquals(0, result);

        String content =
                Files.readString(tempDir.resolve("nsservice/sys1/sys1.wanaku-rules.yaml"), StandardCharsets.UTF_8);
        assertTrue(content.contains("production"));
    }

    @Test
    void testExposeMissingIndexProperties() throws Exception {
        tempDir.resolve("empty").toFile().mkdirs();

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("empty").toAbsolutePath().toString());

        Integer result = cmd.doCall(terminal, printer);
        assertEquals(1, result);
    }

    @Test
    void testExposeMultipleRouteIds() throws Exception {
        setupServiceDir("multiroute", "sys1");

        File routeFile = tempDir.resolve("multiroute/sys1/sys1.camel.yaml").toFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(routeFile, StandardCharsets.UTF_8))) {
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
        setField(cmd, "path", tempDir.resolve("multiroute").toAbsolutePath().toString());

        Integer result = cmd.doCall(terminal, printer);
        assertEquals(0, result);

        String content =
                Files.readString(tempDir.resolve("multiroute/sys1/sys1.wanaku-rules.yaml"), StandardCharsets.UTF_8);
        assertTrue(content.contains("route-alpha"));
        assertTrue(content.contains("route-beta"));
    }

    @Test
    void testExposeGeneratesResourceRules() throws Exception {
        setupServiceDir("resservice", "sys1");

        File routeFile = tempDir.resolve("resservice/sys1/sys1.camel.yaml").toFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(routeFile, StandardCharsets.UTF_8))) {
            pw.println("- route:");
            pw.println("    id: s3-read");
            pw.println("    from:");
            pw.println("      uri: \"direct:start\"");
        }

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("resservice").toAbsolutePath().toString());
        setField(cmd, "type", "resource");

        Integer result = cmd.doCall(terminal, printer);
        assertEquals(0, result);

        String content =
                Files.readString(tempDir.resolve("resservice/sys1/sys1.wanaku-rules.yaml"), StandardCharsets.UTF_8);
        assertTrue(content.contains("resources:"), "Should contain resources section");
        assertFalse(content.contains("tools:"), "Should not contain tools section");
        assertTrue(content.contains("s3-read"), "Should contain route ID");
        assertTrue(content.contains("uri: \"wanaku://s3-read\""), "Should contain default URI");
        assertTrue(content.contains("mimeType: \"application/octet-stream\""), "Should contain default MIME type");
        assertFalse(content.contains("wanaku_body"), "Resources should not include wanaku_body property");
    }

    @Test
    void testExposeResourceWithNamespace() throws Exception {
        setupServiceDir("resns", "sys1");

        File routeFile = tempDir.resolve("resns/sys1/sys1.camel.yaml").toFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(routeFile, StandardCharsets.UTF_8))) {
            pw.println("- route:");
            pw.println("    id: ftp-read");
            pw.println("    from:");
            pw.println("      uri: \"direct:start\"");
        }

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("resns").toAbsolutePath().toString());
        setField(cmd, "type", "resource");
        setField(cmd, "namespaceOptions", createNamespaceOptions("staging"));

        Integer result = cmd.doCall(terminal, printer);
        assertEquals(0, result);

        String content = Files.readString(tempDir.resolve("resns/sys1/sys1.wanaku-rules.yaml"), StandardCharsets.UTF_8);
        assertTrue(content.contains("resources:"));
        assertTrue(content.contains("staging"));
    }

    @Test
    void testExposeResourceCustomUriAndMimeType() throws Exception {
        setupServiceDir("rescustom", "sys1");

        File routeFile = tempDir.resolve("rescustom/sys1/sys1.camel.yaml").toFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(routeFile, StandardCharsets.UTF_8))) {
            pw.println("- route:");
            pw.println("    id: blob-read");
            pw.println("    from:");
            pw.println("      uri: \"direct:start\"");
        }

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("rescustom").toAbsolutePath().toString());
        setField(cmd, "type", "resource");
        setField(cmd, "uri", "azure://container/blob");
        setField(cmd, "mimeType", "text/plain");

        Integer result = cmd.doCall(terminal, printer);
        assertEquals(0, result);

        String content =
                Files.readString(tempDir.resolve("rescustom/sys1/sys1.wanaku-rules.yaml"), StandardCharsets.UTF_8);
        assertTrue(content.contains("uri: \"azure://container/blob\""), "Should use custom URI");
        assertTrue(content.contains("mimeType: \"text/plain\""), "Should use custom MIME type");
    }

    @Test
    void testExposeInvalidType() throws Exception {
        setupServiceDir("invalidtype", "sys1");

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("invalidtype").toAbsolutePath().toString());
        setField(cmd, "type", "invalid");

        Integer result = cmd.doCall(terminal, printer);
        assertEquals(1, result);
    }

    @Test
    void testExposeIgnoresStepLevelIds() throws Exception {
        setupServiceDir("stepids", "sys1");

        File routeFile = tempDir.resolve("stepids/sys1/sys1.camel.yaml").toFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(routeFile, StandardCharsets.UTF_8))) {
            pw.println("- route:");
            pw.println("    id: get-employee-information");
            pw.println("    description: Retrieve employee basic information");
            pw.println("    from:");
            pw.println("      uri: direct:employee-information");
            pw.println("      steps:");
            pw.println("        - setHeader:");
            pw.println("            id: set-method-header");
            pw.println("            constant: GET");
            pw.println("        - bean:");
            pw.println("            id: call-employee-service");
            pw.println("            ref: employeeBean");
        }

        ServiceExpose cmd = new ServiceExpose();
        setField(cmd, "path", tempDir.resolve("stepids").toAbsolutePath().toString());

        Integer result = cmd.doCall(terminal, printer);
        assertEquals(0, result);

        String content =
                Files.readString(tempDir.resolve("stepids/sys1/sys1.wanaku-rules.yaml"), StandardCharsets.UTF_8);
        assertTrue(content.contains("get-employee-information"), "Should contain route ID");
        assertFalse(content.contains("set-method-header"), "Should not contain step ID");
        assertFalse(content.contains("call-employee-service"), "Should not contain step ID");
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

            new File(sysDir, sys + ".camel.yaml").createNewFile();
            new File(sysDir, sys + ".wanaku-rules.yaml").createNewFile();
        }

        File indexFile = new File(rootDir, "index.properties");
        try (FileWriter fw = new FileWriter(indexFile, StandardCharsets.UTF_8)) {
            props.store(fw, null);
        }
    }

    private NamespaceOptions createNamespaceOptions(String namespace) throws Exception {
        NamespaceOptions options = new NamespaceOptions();
        setField(options, "namespace", namespace);
        return options;
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
