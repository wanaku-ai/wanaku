package ai.wanaku.cli.main.support;

import java.io.IOException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.WINDOWS)
@Timeout(value = 60)
class WanakuPrinterPagerTest {

    @Test
    void testPageMarkdownRejectsNull() throws IOException {
        Terminal terminal = TerminalBuilder.builder().dumb(true).build();
        WanakuPrinter printer = new WanakuPrinter(terminal);

        assertThrows(IllegalArgumentException.class, () -> printer.pageMarkdown(null));
    }

    @Test
    void testPageMarkdownMethodExists() throws IOException {
        Terminal terminal = TerminalBuilder.builder().dumb(true).build();
        WanakuPrinter printer = new WanakuPrinter(terminal);

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
        WanakuPrinter printer = new WanakuPrinter(terminal);

        String markdown = "# Test\n\nThis is a **test**.";
        assertDoesNotThrow(() -> printer.printMarkdown(markdown));
    }
}
