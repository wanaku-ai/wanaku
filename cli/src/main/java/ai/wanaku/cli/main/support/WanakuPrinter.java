package ai.wanaku.cli.main.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jline.builtins.ConfigurationPath;
import org.jline.builtins.Styles;
import org.jline.console.impl.DefaultPrinter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toMap;
import static org.jline.console.Printer.TableRows.EVEN;

/**
 * Enhanced printer utility for the Wanaku CLI application that provides formatted output capabilities.
 *
 * <p>This class extends JLine's {@link DefaultPrinter} to provide a rich set of printing methods
 * with consistent styling and formatting for terminal output. It supports various output formats
 * including styled messages, tables, and object representations.</p>
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>Styled message printing (error, warning, info, success)</li>
 *   <li>Table printing with customizable options and column selection</li>
 *   <li>Object-to-map conversion and printing</li>
 *   <li>Exception highlighting with configurable display modes</li>
 *   <li>Terminal color and ANSI support</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * WanakuPrinter printer = new WanakuPrinter(configPath, terminal);
 * printer.printSuccessMessage("Operation completed successfully");
 * printer.printTable(dataList, "name", "status", "timestamp");
 * printer.printAsMap(configuration, "host", "port", "enabled");
 * }</pre>
 *
 * @author Wanaku CLI Team
 * @version 1.0
 * @since 1.0
 * @see DefaultPrinter
 * @see Terminal
 */
public class WanakuPrinter extends DefaultPrinter {

    // Styling constants for different message types
    /** Style for error messages - bold red text. */
    private static final AttributedStyle ERROR_STYLE = AttributedStyle.BOLD.foreground(AttributedStyle.RED);

    /** Style for warning messages - yellow text. */
    private static final AttributedStyle WARNING_STYLE = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);

    /** Style for informational messages - blue text. */
    private static final AttributedStyle INFO_STYLE = AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE);

    /** Style for success messages - bold green text. */
    private static final AttributedStyle SUCCESS_STYLE = AttributedStyle.BOLD.foreground(AttributedStyle.GREEN);

    // Table formatting constants
    /** Exception display mode for stack trace output. */
    private static final String EXCEPTION_MODE_STACK = "stack";

    /** Default exception display mode configuration key. */
    private static final String EXCEPTION_OPTION_KEY = "exception";

    /**
     * Default options for table printing.
     * Configures structured table display with row highlighting and single-row table support.
     */
    private static final Map<String, Object> DEFAULT_TABLE_OPTIONS = Collections.unmodifiableMap(
            Map.of(
                    STRUCT_ON_TABLE, TRUE,
                    ROW_HIGHLIGHT, EVEN,
                    ONE_ROW_TABLE, TRUE
            )
    );

    /**
     * Default options for map printing.
     * Configures unstructured display without special table formatting.
     */
    private static final Map<String, Object> DEFAULT_MAP_OPTIONS = Map.of(
            STRUCT_ON_TABLE, FALSE,
            ROW_HIGHLIGHT, EVEN,
            ONE_ROW_TABLE, FALSE
    );

    /** The terminal instance used for all output operations. */
    private final Terminal terminal;

    /** Jackson ObjectMapper instance for converting objects to maps for table display. */
    private final ObjectMapper objectMapper;

    /**
     * Constructs a new WanakuPrinter with the specified configuration and terminal.
     *
     * @param configPath the configuration path for printer settings
     * @param terminal the terminal instance to use for output operations
     * @throws IllegalArgumentException if terminal is null
     */
    public WanakuPrinter(ConfigurationPath configPath, Terminal terminal) {
        super(configPath);
        this.terminal = validateNotNull(terminal, "Terminal cannot be null");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a new terminal instance with system integration, Jansi support, and color support enabled.
     *
     * <p>This factory method provides a pre-configured terminal suitable for CLI applications
     * that require color output and system integration.</p>
     *
     * @return a new terminal instance with standard CLI configuration
     * @throws IOException if the terminal cannot be created due to I/O issues
     */
    public static Terminal terminalInstance() throws IOException {
        return TerminalBuilder
                .builder()
                .system(true)
                .jansi(true)
                .color(true)
                .build();
    }

    /**
     * Returns the terminal instance used by this printer.
     *
     * @return the terminal instance for output operations
     */
    @Override
    protected Terminal terminal() {
        return terminal;
    }

    /**
     * Prints a list of objects as a table with all available columns.
     *
     * <p>This is a convenience method that displays all properties of the objects
     * in the list as table columns using default table formatting options.</p>
     *
     * @param <T> the type of objects in the list
     * @param printables the list of objects to display as a table
     */
    public <T> void printTable(List<T> printables) {
        printTable(printables, new String[]{});
    }

    /**
     * Prints a list of objects as a table with specified columns.
     *
     * <p>Displays only the specified columns from the objects in the list.
     * Uses default table formatting options.</p>
     *
     * @param <T> the type of objects in the list
     * @param printables the list of objects to display as a table
     * @param columns the column names to include in the table output
     */
    public <T> void printTable(List<T> printables, String... columns) {
        printTable(DEFAULT_TABLE_OPTIONS, printables, columns);
    }

    /**
     * Prints a list of objects as a table with custom formatting options and specified columns.
     *
     * <p>Provides full control over table appearance through custom options while
     * using the default object-to-map conversion strategy.</p>
     *
     * @param <T> the type of objects in the list
     * @param options custom formatting options for table display
     * @param printables the list of objects to display as a table
     * @param columns the column names to include in the table output
     */
    public <T> void printTable(Map<String, Object> options, List<T> printables, String... columns) {
        printTable(options, printables, this::convertToMap, columns);
    }

    /**
     * Prints a list of objects as a table with full customization options.
     *
     * <p>This is the most flexible table printing method, allowing custom formatting options,
     * custom object-to-map conversion logic, and column selection. Handles empty or null
     * input gracefully and provides error handling for conversion failures.</p>
     *
     * @param <T> the type of objects in the list
     * @param options custom formatting options for table display
     * @param objectsToPrint the list of objects to display as a table
     * @param toMap function to convert objects to map representation
     * @param columns the column names to include in the table output
     */
    public <T> void printTable(Map<String, Object> options, List<T> objectsToPrint,
                               Function<T, Map<String, Object>> toMap, String... columns) {
        if (objectsToPrint == null || objectsToPrint.isEmpty()) {
            return;
        }

        try {
            List<Map<String, Object>> mappedObjects = objectsToPrint.stream()
                    .map(toMap)
                    .toList();

            Map<String, Object> mergedOptions = createMergedOptions(options);

            if (columns != null && columns.length > 0) {
                mergedOptions.put(COLUMNS, Arrays.asList(columns));
            }

            println(mergedOptions, mappedObjects);
        } catch (Exception e) {
            printErrorMessage("Failed to print table: " + e.getMessage());
        }
    }

    /**
     * Prints an object as a map with all available properties.
     *
     * <p>Converts the object to a map representation and displays all key-value pairs
     * using default map formatting options.</p>
     *
     * @param <T> the type of the object to print
     * @param object the object to display as a map
     */
    public <T> void printAsMap(T object) {
        printAsMap(object, new String[]{});
    }

    /**
     * Prints an object as a map with only specified keys.
     *
     * <p>Converts the object to a map representation and displays only the key-value
     * pairs for the specified keys. Keys not present in the object are silently ignored.</p>
     *
     * @param <T> the type of the object to print
     * @param object the object to display as a map
     * @param keys the specific keys to include in the output
     */
    public <T> void printAsMap(T object, String... keys) {
        if (object == null) {
            return;
        }

        Map<String, Object> map = convertToMap(object);
        if (keys != null && keys.length > 0) {
            Set<String> keySet = Set.of(keys);
            map = map.entrySet()
                    .stream()
                    .filter(entry -> keySet.contains(entry.getKey()))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        try {
            println(DEFAULT_MAP_OPTIONS, map);
        } catch (Exception e) {
            printErrorMessage("Failed to print object: " + e.getMessage());
        }
    }

    /**
     * Prints an error message with red styling.
     *
     * <p>Uses bold red text to display error messages. Null messages are silently ignored.</p>
     *
     * @param message the error message to display, null values are ignored
     */
    public void printErrorMessage(String message) {
        if (message != null) {
            printStyledMessage(message, ERROR_STYLE);
        }
    }

    /**
     * Prints a warning message with yellow styling.
     *
     * <p>Uses yellow text to display warning messages. Null messages are silently ignored.</p>
     *
     * @param message the warning message to display, null values are ignored
     */
    public void printWarningMessage(String message) {
        if (message != null) {
            printStyledMessage(message, WARNING_STYLE);
        }
    }

    /**
     * Prints an informational message with blue styling.
     *
     * <p>Uses blue text to display informational messages. Null messages are silently ignored.</p>
     *
     * @param message the informational message to display, null values are ignored
     */
    public void printInfoMessage(String message) {
        if (message != null) {
            printStyledMessage(message, INFO_STYLE);
        }
    }

    /**
     * Prints a success message with green styling.
     *
     * <p>Uses bold green text to display success messages. Null messages are silently ignored.</p>
     *
     * @param message the success message to display, null values are ignored
     */
    public void printSuccessMessage(String message) {
        if (message != null) {
            printStyledMessage(message, SUCCESS_STYLE);
        }
    }

    /**
     * Highlights and prints exception information based on configured display mode.
     *
     * <p>Supports two display modes:</p>
     * <ul>
     *   <li><strong>stack</strong> (default): Prints full stack trace to stderr</li>
     *   <li><strong>message</strong>: Prints only the exception message with emphasis styling</li>
     * </ul>
     *
     * <p>The display mode is controlled by the "exception" option in the provided options map.</p>
     *
     * @param options configuration options including exception display mode
     * @param exception the exception to display, null exceptions are silently ignored
     */
    @Override
    protected void highlightAndPrint(Map<String, Object> options, Throwable exception) {
        if (exception == null) {
            return;
        }

        String exceptionMode = (String) options.getOrDefault(EXCEPTION_OPTION_KEY, EXCEPTION_MODE_STACK);

        if (EXCEPTION_MODE_STACK.equals(exceptionMode)) {
            exception.printStackTrace();
        } else {
            AttributedStringBuilder builder = new AttributedStringBuilder();
            builder.append(exception.getMessage(), Styles.prntStyle().resolve(".em"));
            builder.toAttributedString().println(terminal());
        }
    }

    /**
     * Converts an object to a Map representation for table/map display.
     *
     * <p>Uses Jackson ObjectMapper to perform the conversion, which handles most
     * Java objects including POJOs, records, and collections. The conversion
     * respects Jackson annotations if present on the object.</p>
     *
     * @param obj the object to convert to a map
     * @return a map representation of the object, empty map if input is null
     * @throws IllegalArgumentException if the object cannot be converted to a map
     */
    private Map<String, Object> convertToMap(Object obj) {
        if (obj == null) {
            return Map.of();
        }

        try {
            return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert object to map: " + e.getMessage(), e);
        }
    }

    /**
     * Prints a message with the specified styling.
     *
     * <p>Applies the given AttributedStyle to the message and outputs it to the terminal
     * with proper ANSI escape sequences. Ensures the output is immediately flushed.</p>
     *
     * @param message the message to print
     * @param style the styling to apply to the message
     */
    private void printStyledMessage(String message, AttributedStyle style) {
        AttributedString styledMessage = new AttributedStringBuilder()
                .style(style)
                .append(message)
                .toAttributedString();

        terminal.writer().println(styledMessage.toAnsi());
        terminal.flush();
    }

    /**
     * Creates a new options map by merging custom options with default table options.
     *
     * <p>Default options are applied first, then custom options override any conflicting
     * settings. This ensures consistent baseline behavior while allowing customization.</p>
     *
     * @param customOptions custom options to merge with defaults, null is handled gracefully
     * @return a new map containing merged options
     */
    private Map<String, Object> createMergedOptions(Map<String, Object> customOptions) {
        Map<String, Object> merged = new HashMap<>(DEFAULT_TABLE_OPTIONS);
        if (customOptions != null) {
            merged.putAll(customOptions);
        }
        return merged;
    }

    /**
     * Validates that an object is not null and returns it.
     *
     * <p>This utility method provides consistent null validation with custom error messages
     * throughout the class.</p>
     *
     * @param <T> the type of the object to validate
     * @param object the object to validate
     * @param message the error message to use if validation fails
     * @return the validated object
     * @throws IllegalArgumentException if the object is null
     */
    private static <T> T validateNotNull(T object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
        return object;
    }
}