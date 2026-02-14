package ai.wanaku.cli.main.support;

import java.io.IOException;
import java.nio.file.Path;
import org.jline.builtins.ConfigurationPath;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for WanakuPrinter pager functionality.
 *
 * <p>Note: The pageMarkdown method requires an interactive terminal, so these tests
 * verify the method exists and handles null input correctly. Full integration testing
 * requires a real terminal environment.</p>
 */
@DisabledOnOs(OS.WINDOWS)
@Timeout(value = 60)
class WanakuPrinterPagerTest {

    @Test
    void testPageMarkdownRejectsNull() throws IOException {
        Terminal terminal = TerminalBuilder.builder().dumb(true).build();
        ConfigurationPath configPath = new ConfigurationPath((Path) null, null);
        WanakuPrinter printer = new WanakuPrinter(configPath, terminal);

        assertThrows(IllegalArgumentException.class, () -> printer.pageMarkdown(null));
    }

    @Test
    void testPageMarkdownMethodExists() throws IOException {
        Terminal terminal = TerminalBuilder.builder().dumb(true).build();
        ConfigurationPath configPath = new ConfigurationPath((Path) null, null);
        WanakuPrinter printer = new WanakuPrinter(configPath, terminal);

        // Verify the method exists by checking it's available via reflection
        // We don't actually call it in tests since it requires an interactive terminal
        boolean methodExists = false;
        try {
            var method = WanakuPrinter.class.getMethod("pageMarkdown", String.class);
            methodExists = method != null;
        } catch (NoSuchMethodException e) {
            // Method doesn't exist
        }

        assertTrue(methodExists, "pageMarkdown method should exist in WanakuPrinter");
    }

    @Test
    void testPrintMarkdownStillWorks() throws IOException {
        Terminal terminal = TerminalBuilder.builder().dumb(true).build();
        ConfigurationPath configPath = new ConfigurationPath((Path) null, null);
        WanakuPrinter printer = new WanakuPrinter(configPath, terminal);

        // Verify printMarkdown still works for non-paged output
        String markdown = "# Test\n\nThis is a **test**.";
        assertDoesNotThrow(() -> printer.printMarkdown(markdown));
    }
}
