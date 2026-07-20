package ai.wanaku.cli.main.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.WINDOWS)
class WanakuPrinterPlainModeTest {

    private ByteArrayOutputStream outputStream;
    private Terminal terminal;
    private WanakuPrinter printer;

    @BeforeEach
    void setUp() throws IOException {
        WanakuPrinter.setPlainMode(true);
        outputStream = new ByteArrayOutputStream();
        terminal = TerminalBuilder.builder()
                .system(false)
                .streams(System.in, outputStream)
                .jni(false)
                .color(false)
                .build();
        printer = new WanakuPrinter(null, terminal);
    }

    @AfterEach
    void tearDown() throws IOException {
        WanakuPrinter.setPlainMode(false);
        if (terminal != null) {
            terminal.close();
        }
    }

    @Test
    void printTableProducesNonEmptyOutput() {
        List<Map<String, Object>> data = List.of(
                Map.of("name", "activemq6-tool", "description", "ActiveMQ 6 tool template"),
                Map.of("name", "kafka-tool", "description", "Kafka tool template"));

        printer.printTable(data, "name", "description");

        String output = outputStream.toString();
        assertFalse(output.isBlank(), "Plain-mode table output must not be blank");
        assertTrue(output.contains("activemq6-tool"), "Output must contain row data 'activemq6-tool'");
        assertTrue(output.contains("kafka-tool"), "Output must contain row data 'kafka-tool'");
    }

    @Test
    void printTableIncludesHeaderRow() {
        List<Map<String, Object>> data = List.of(Map.of("name", "sql-tool", "description", "SQL tool template"));

        printer.printTable(data, "name", "description");

        String output = outputStream.toString();
        assertTrue(output.contains("name"), "Output must contain column header 'name'");
        assertTrue(output.contains("description"), "Output must contain column header 'description'");
    }

    @Test
    void printTableHandlesEmptyList() {
        printer.printTable(List.of(), "name", "description");
        String output = outputStream.toString();
        assertTrue(output.isEmpty(), "Empty list should produce no output");
    }

    @Test
    void printTableHandlesNullList() {
        printer.printTable(null, "name", "description");
        String output = outputStream.toString();
        assertTrue(output.isEmpty(), "Null list should produce no output");
    }

    @Test
    void printTableWithoutColumnsUsesMapKeys() {
        List<Map<String, Object>> data = List.of(Map.of("name", "test-tool", "uri", "http://example.com"));

        printer.printTable(data);

        String output = outputStream.toString();
        assertFalse(output.isBlank(), "Output must not be blank");
        assertTrue(output.contains("test-tool"), "Output must contain row value");
    }

    @Test
    void printAsMapProducesNonEmptyOutput() {
        Map<String, Object> data = Map.of("name", "my-capability", "host", "localhost", "port", 9000);

        printer.printAsMap(data, "name", "host", "port");

        String output = outputStream.toString();
        assertFalse(output.isBlank(), "Plain-mode map output must not be blank");
        assertTrue(output.contains("my-capability"), "Output must contain value 'my-capability'");
        assertTrue(output.contains("localhost"), "Output must contain value 'localhost'");
    }

    @Test
    void printAsMapHandlesNullValues() {
        // Simulates a Namespace with null name (pre-allocated, no --name flag)
        Map<String, Object> data = new HashMap<>();
        data.put("id", "ns-uuid-123");
        data.put("name", null);
        data.put("path", "cleanup-target");

        assertDoesNotThrow(
                () -> printer.printAsMap(data, "id", "name", "path"),
                "printAsMap must not throw when map values are null");

        String output = outputStream.toString();
        assertTrue(output.contains("ns-uuid-123"), "Output must contain non-null value 'ns-uuid-123'");
        assertTrue(output.contains("cleanup-target"), "Output must contain non-null value 'cleanup-target'");
    }

    @Test
    void printInfoMessageProducesOutput() {
        printer.printInfoMessage("Available service templates:");

        String output = outputStream.toString();
        assertTrue(output.contains("Available service templates:"), "Info message must appear in plain-mode output");
    }

    @Test
    void printTableRowsAreNotBlank() {
        List<Map<String, Object>> data = List.of(
                Map.of("name", "template-1", "description", "First"),
                Map.of("name", "template-2", "description", "Second"),
                Map.of("name", "template-3", "description", "Third"));

        printer.printTable(data, "name", "description");

        String output = outputStream.toString();
        String[] lines = output.split("\n");
        // Header + 3 data rows = at least 4 lines
        assertTrue(lines.length >= 4, "Expected at least 4 lines (1 header + 3 data), got " + lines.length);

        for (int i = 1; i < lines.length; i++) {
            assertFalse(lines[i].isBlank(), "Data row " + i + " must not be blank");
        }
    }
}
