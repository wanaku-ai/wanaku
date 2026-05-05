package ai.wanaku.cli.main.support;

import dev.tamboui.text.Text;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownRendererTest {

    @Test
    void testRenderSimpleText() {
        String markdown = "Hello World";
        Text result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.rawContent().contains("Hello World"));
    }

    @Test
    void testRenderHeading() {
        String markdown = "# Main Heading";
        Text result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.rawContent().contains("Main Heading"));
    }

    @Test
    void testRenderBold() {
        String markdown = "This is **bold** text";
        Text result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.rawContent().contains("bold"));
    }

    @Test
    void testRenderCode() {
        String markdown = "Use the `code` command";
        Text result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.rawContent().contains("code"));
    }

    @Test
    void testRenderList() {
        String markdown =
                """
                - Item 1
                - Item 2
                - Item 3
                """;
        Text result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.rawContent().contains("Item 1"));
        assertTrue(result.rawContent().contains("Item 2"));
        assertTrue(result.rawContent().contains("Item 3"));
    }

    @Test
    void testRenderComplexMarkdown() {
        String markdown =
                """
                # Usage Guide

                Filter tools using **label expressions** with the `-l` option.

                ## Syntax
                - `key=value` - Equality
                - `key!=value` - Inequality
                - Use `&` for AND, `|` for OR

                *Note:* Expressions are case-sensitive.
                """;

        Text result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.rawContent().contains("Usage Guide"));
        assertTrue(result.rawContent().contains("label expressions"));
        assertTrue(result.rawContent().contains("Syntax"));
        assertTrue(result.rawContent().contains("case-sensitive"));
    }

    @Test
    void testRenderLink() {
        String markdown = "Visit [Wanaku](https://wanaku.ai) for more info";
        Text result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.rawContent().contains("Wanaku"));
        assertTrue(result.rawContent().contains("https://wanaku.ai"));
    }

    @Test
    void testNullMarkdownThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> MarkdownRenderer.render(null));
    }

    @Test
    void testEmptyMarkdown() {
        String markdown = "";
        Text result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
    }

    @Test
    void testMarkdownWithMultipleParagraphs() {
        String markdown =
                """
                First paragraph.

                Second paragraph.

                Third paragraph.
                """;

        Text result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.rawContent().contains("First paragraph"));
        assertTrue(result.rawContent().contains("Second paragraph"));
        assertTrue(result.rawContent().contains("Third paragraph"));
    }

    @Test
    void testRenderTable() {
        String markdown =
                """
                | Header 1 | Header 2 |
                |----------|----------|
                | Cell 1   | Cell 2   |
                | Cell 3   | Cell 4   |
                """;

        Text result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        String output = result.rawContent();

        assertTrue(output.contains("Header 1"));
        assertTrue(output.contains("Header 2"));
        assertTrue(output.contains("Cell 1"));
        assertTrue(output.contains("Cell 2"));
        assertTrue(output.contains("Cell 3"));
        assertTrue(output.contains("Cell 4"));
        assertTrue(output.contains("┌") || output.contains("│"));
    }

    @Test
    void testRenderComplexTable() {
        String markdown =
                """
                | Operator | Description | Example |
                |----------|-------------|---------|
                | `=` | Equals | `category=weather` |
                | `!=` | Not equals | `status!=deprecated` |
                """;

        Text result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        String output = result.rawContent();
        assertTrue(output.contains("Operator"));
        assertTrue(output.contains("Description"));
        assertTrue(output.contains("Example"));
        assertTrue(output.contains("Equals"));
        assertTrue(output.contains("Not equals"));
    }
}
