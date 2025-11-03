package ai.wanaku.cli.main.support;

import static org.junit.jupiter.api.Assertions.*;

import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MarkdownRenderer.
 */
class MarkdownRendererTest {

    @Test
    void testRenderSimpleText() {
        String markdown = "Hello World";
        AttributedString result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.toString().contains("Hello World"));
    }

    @Test
    void testRenderHeading() {
        String markdown = "# Main Heading";
        AttributedString result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.toString().contains("Main Heading"));
    }

    @Test
    void testRenderBold() {
        String markdown = "This is **bold** text";
        AttributedString result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.toString().contains("bold"));
    }

    @Test
    void testRenderCode() {
        String markdown = "Use the `code` command";
        AttributedString result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.toString().contains("code"));
    }

    @Test
    void testRenderList() {
        String markdown =
                """
                - Item 1
                - Item 2
                - Item 3
                """;
        AttributedString result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.toString().contains("Item 1"));
        assertTrue(result.toString().contains("Item 2"));
        assertTrue(result.toString().contains("Item 3"));
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

        AttributedString result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.toString().contains("Usage Guide"));
        assertTrue(result.toString().contains("label expressions"));
        assertTrue(result.toString().contains("Syntax"));
        assertTrue(result.toString().contains("case-sensitive"));
    }

    @Test
    void testRenderLink() {
        String markdown = "Visit [Wanaku](https://wanaku.ai) for more info";
        AttributedString result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.toString().contains("Wanaku"));
        assertTrue(result.toString().contains("https://wanaku.ai"));
    }

    @Test
    void testNullMarkdownThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> MarkdownRenderer.render(null));
    }

    @Test
    void testEmptyMarkdown() {
        String markdown = "";
        AttributedString result = MarkdownRenderer.render(markdown);

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

        AttributedString result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        assertTrue(result.toString().contains("First paragraph"));
        assertTrue(result.toString().contains("Second paragraph"));
        assertTrue(result.toString().contains("Third paragraph"));
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

        AttributedString result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        String output = result.toString();

        assertTrue(output.contains("Header 1"));
        assertTrue(output.contains("Header 2"));
        assertTrue(output.contains("Cell 1"));
        assertTrue(output.contains("Cell 2"));
        assertTrue(output.contains("Cell 3"));
        assertTrue(output.contains("Cell 4"));
        // Check for table borders
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

        AttributedString result = MarkdownRenderer.render(markdown);

        assertNotNull(result);
        String output = result.toString();
        assertTrue(output.contains("Operator"));
        assertTrue(output.contains("Description"));
        assertTrue(output.contains("Example"));
        assertTrue(output.contains("Equals"));
        assertTrue(output.contains("Not equals"));
    }
}
