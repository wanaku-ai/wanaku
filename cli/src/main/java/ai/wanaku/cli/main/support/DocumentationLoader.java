package ai.wanaku.cli.main.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Utility class for loading documentation resources in both JVM and native mode.
 *
 * <p>This class provides methods to load Markdown documentation files from the classpath
 * in a way that works correctly in both regular JVM mode and GraalVM native image mode.</p>
 *
 * <p>Resources are loaded using the thread context class loader, which ensures compatibility
 * with Quarkus native compilation.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Load label expressions documentation
 * String markdown = DocumentationLoader.loadLabelExpressionsDoc();
 * printer.printMarkdown(markdown);
 *
 * // Load custom documentation
 * String custom = DocumentationLoader.loadDoc("docs/MY_DOC.md");
 * }</pre>
 *
 */
public class DocumentationLoader {

    /** Path to the label expressions documentation. */
    private static final String LABEL_EXPRESSIONS_DOC = "docs/LABEL_EXPRESSIONS.md";

    /**
     * Private constructor to prevent instantiation.
     */
    private DocumentationLoader() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Loads the label expressions documentation.
     *
     * <p>This is a convenience method that loads the standard label expressions
     * documentation file.</p>
     *
     * @return the label expressions documentation as a String
     * @throws DocumentationNotFoundException if the documentation file cannot be found
     * @throws IOException if an error occurs reading the documentation
     */
    public static String loadLabelExpressionsDoc() throws IOException {
        return loadDoc(LABEL_EXPRESSIONS_DOC);
    }

    /**
     * Loads a documentation file from the classpath.
     *
     * <p>The path should be relative to the resources directory. For example,
     * to load {@code src/main/resources/docs/MY_DOC.md}, use {@code "docs/MY_DOC.md"}.</p>
     *
     * <p>This method works correctly in both JVM and GraalVM native image mode,
     * provided the resource is included in the native image configuration.</p>
     *
     * @param resourcePath the path to the documentation file (relative to resources directory)
     * @return the documentation content as a String
     * @throws DocumentationNotFoundException if the documentation file cannot be found
     * @throws IOException if an error occurs reading the documentation
     */
    public static String loadDoc(String resourcePath) throws IOException {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource path cannot be null or empty");
        }

        // Use thread context class loader for Quarkus compatibility
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = DocumentationLoader.class.getClassLoader();
        }

        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new DocumentationNotFoundException("Documentation file not found: " + resourcePath
                        + ". Ensure the file exists in src/main/resources/"
                        + " and is included in quarkus.native.resources.includes");
            }

            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    /**
     * Exception thrown when a documentation file cannot be found.
     */
    public static class DocumentationNotFoundException extends IOException {
        /**
         * Constructs a new DocumentationNotFoundException with the specified message.
         *
         * @param message the detail message
         */
        public DocumentationNotFoundException(String message) {
            super(message);
        }
    }
}
