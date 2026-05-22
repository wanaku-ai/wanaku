package ai.wanaku.cli.main.support;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jline.builtins.ConfigurationPath;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelHelperTest {

    private WanakuPrinter printer;

    @BeforeEach
    void setUp() throws IOException {
        Terminal terminal = TerminalBuilder.builder().dumb(true).build();
        ConfigurationPath configPath = new ConfigurationPath((Path) null, null);
        printer = new WanakuPrinter(configPath, terminal);
    }

    @Test
    void parseLabelsReturnsEmptyMapWhenLabelsIsNull() {
        Map<String, String> result = LabelHelper.parseLabels(null, printer);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseLabelsReturnsEmptyMapWhenLabelsIsEmpty() {
        Map<String, String> result = LabelHelper.parseLabels(List.of(), printer);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseLabelsParsesValidLabels() {
        Map<String, String> result = LabelHelper.parseLabels(List.of("env=prod", "team=backend"), printer);

        assertEquals(2, result.size());
        assertEquals("prod", result.get("env"));
        assertEquals("backend", result.get("team"));
    }

    @Test
    void parseLabelsReturnsNullForInvalidFormat() {
        Map<String, String> result = LabelHelper.parseLabels(List.of("no-equals-sign"), printer);

        assertTrue(result == null || result.isEmpty());
    }

    @Test
    void parseLabelsTrimsKeysAndValues() {
        Map<String, String> result = LabelHelper.parseLabels(List.of(" env = prod "), printer);

        assertEquals(1, result.size());
        assertEquals("prod", result.get("env"));
    }
}
