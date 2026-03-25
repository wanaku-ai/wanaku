package ai.wanaku.cli.main.support;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for DocumentationLoader.
 */
class DocumentationLoaderTest {

    @Test
    void testLoadLabelExpressionsDoc() throws IOException {
        String doc = DocumentationLoader.loadLabelExpressionsDoc();

        assertNotNull(doc);
        assertFalse(doc.isEmpty());
        assertTrue(doc.contains("Label Expressions Guide"));
        assertTrue(doc.contains("## Overview"));
    }

    @Test
    void testLoadDocWithValidPath() throws IOException {
        String doc = DocumentationLoader.loadDoc("docs/LABEL_EXPRESSIONS.md");

        assertNotNull(doc);
        assertFalse(doc.isEmpty());
    }

    @Test
    void testLoadDocWithInvalidPathThrowsException() {
        assertThrows(
                DocumentationLoader.DocumentationNotFoundException.class,
                () -> DocumentationLoader.loadDoc("docs/NONEXISTENT.md"));
    }

    @Test
    void testLoadDocWithNullPathThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> DocumentationLoader.loadDoc(null));
    }

    @Test
    void testLoadDocWithEmptyPathThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> DocumentationLoader.loadDoc(""));
    }

    @Test
    void testLoadDocWithWhitespacePathThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> DocumentationLoader.loadDoc("   "));
    }

    @Test
    void testLoadedDocContainsExpectedSections() throws IOException {
        String doc = DocumentationLoader.loadLabelExpressionsDoc();

        // Check for major sections
        assertTrue(doc.contains("## Quick Start"));
        assertTrue(doc.contains("## Expression Syntax"));
        assertTrue(doc.contains("## Expression Examples"));
        assertTrue(doc.contains("## Operator Precedence"));
        assertTrue(doc.contains("## Common Use Cases"));
        assertTrue(doc.contains("## Parser Safety"));
        assertTrue(doc.contains("## Troubleshooting"));
    }

    @Test
    void testLoadedDocContainsCodeExamples() throws IOException {
        String doc = DocumentationLoader.loadLabelExpressionsDoc();

        // Check for code examples
        assertTrue(doc.contains("wanaku tools list"));
        assertTrue(doc.contains("category=weather"));
        assertTrue(doc.contains("environment=production"));
    }
}
