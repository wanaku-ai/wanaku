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
 * A specialized printer implementation that extends JLine's {@link DefaultPrinter} to provide
 * enhanced table printing capabilities with Jackson object mapping support.
 *
 * <p>This class provides convenient methods for printing collections of objects as
 * formatted tables in terminal output, with automatic object-to-map conversion using
 * Jackson's {@link ObjectMapper}. It supports customizable formatting options, column
 * selection, and styled message output for different log levels.</p>
 *
 * <h3>Key Features:</h3>
 * <ul>
 * <li>Automatic conversion of Java objects to printable table format using Jackson</li>
 * <li>Customizable table formatting options with sensible defaults</li>
 * <li>Column selection and filtering capabilities</li>
 * <li>Enhanced exception display formatting with stack trace control</li>
 * <li>Styled message output (error, warning, info, success)</li>
 * <li>Full integration with JLine terminal capabilities</li>
 * </ul>
 *
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // Basic table printing
 * List<Person> people = Arrays.asList(new Person("John", 30), new Person("Jane", 25));
 * printer.printTable(people);
 *
 * // Table with specific columns
 * printer.printTable(people, "name", "age");
 *
 * // Custom formatting options
 * Map<String, Object> options = Map.of("maxColumnWidth", 20);
 * printer.printTable(options, people, "name");
 *
 * // Styled messages
 * printer.printErrorMessage("Operation failed");
 * printer.printSuccessMessage("Operation completed successfully");
 * }</pre>
 *
 * @see DefaultPrinter
 * @see ObjectMapper
 * @see Terminal
 */
public class WanakuPrinter extends DefaultPrinter {

    // Constants for styling
    private static final AttributedStyle ERROR_STYLE = AttributedStyle.BOLD.foreground(AttributedStyle.RED);
    private static final AttributedStyle WARNING_STYLE = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
    private static final AttributedStyle INFO_STYLE = AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE);
    private static final AttributedStyle SUCCESS_STYLE = AttributedStyle.BOLD.foreground(AttributedStyle.GREEN);

    /**
     * Default printing options applied to all table operations unless explicitly overridden.
     *
     * <p>Default configuration:</p>
     * <ul>
     * <li>{@code STRUCT_ON_TABLE}: {@code true} - Enables structured table display</li>
     * <li>{@code ROW_HIGHLIGHT}: {@code EVEN} - Highlights even-numbered rows for better readability</li>
     * <li>{@code ONE_ROW_TABLE}: {@code true} - Optimizes display for single-row tables</li>
     * </ul>
     *
     * @see #printTable(Map, List, Function, String...)
     */
    private static final Map<String, Object> DEFAULT_TABLE_OPTIONS = Collections.unmodifiableMap(
            Map.of(
                    STRUCT_ON_TABLE, TRUE,
                    ROW_HIGHLIGHT, EVEN,
                    ONE_ROW_TABLE, TRUE
            )
    );

    /**
     * Default options for map printing operations.
     *
     * <p>Configuration for object-to-map display:</p>
     * <ul>
     * <li>{@code STRUCT_ON_TABLE}: {@code false} - Flattens objects for map display</li>
     * <li>{@code ROW_HIGHLIGHT}: {@code EVEN} - Highlights even-numbered rows</li>
     * <li>{@code ONE_ROW_TABLE}: {@code false} - Display one row data on table.</li>
     * </ul>
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
     * <p>The printer is initialized with a new {@link ObjectMapper} instance using default
     * Jackson configuration. The terminal instance will be used for all output operations.</p>
     *
     * @param configPath the configuration path for printer settings, must not be {@code null}
     * @param terminal   the terminal instance to use for output operations, must not be {@code null}
     * @throws IllegalArgumentException if configPath or terminal is {@code null}
     */
    public WanakuPrinter(ConfigurationPath configPath, Terminal terminal) {
        super(configPath);
        this.terminal = validateNotNull(terminal, "Terminal cannot be null");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates and returns a JLine 3 Terminal instance configured for system use.
     *
     * <p>This method constructs a terminal with the following configuration:
     * <ul>
     * <li>System terminal mode enabled - uses the actual system terminal</li>
     * <li>Jansi support enabled - provides ANSI escape sequence handling on Windows</li>
     * <li>Color support enabled - allows colored output in the terminal</li>
     * </ul>
     *
     * <p>The terminal instance can be used for advanced console input/output operations,
     * including reading user input, displaying colored text, and handling special key
     * sequences.
     *
     * @return a configured Terminal instance ready for use
     * @throws IOException if the terminal cannot be created or initialized
     * @see org.jline.terminal.Terminal
     * @see org.jline.terminal.TerminalBuilder
     */
    public static Terminal terminalInstance() throws IOException {
        return  TerminalBuilder
                .builder()
                .system(true)
                .jansi(true)
                .color(true)
                .build();
    }

    /**
     * Returns the terminal instance associated with this printer.
     *
     * <p>This method is used internally by the parent {@link DefaultPrinter} class
     * to access the terminal for output operations.</p>
     *
     * @return the terminal used for output operations, never {@code null}
     */
    @Override
    protected Terminal terminal() {
        return terminal;
    }

    // Table Printing Methods

    /**
     * Prints a collection of objects as a formatted table using default settings.
     *
     * <p>This is the simplest table printing method. It uses automatic Jackson-based
     * object-to-map conversion and default formatting options. All object properties
     * will be displayed as columns in the table.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * List<User> users = userService.getAllUsers();
     * printer.printTable(users);
     * }</pre>
     *
     * @param <T>        the type of objects to print
     * @param printables the collection of objects to display; {@code null} collections are ignored
     * @see #printTable(List, String...)
     * @see #printTable(Map, List, String...)
     */
    public <T> void printTable(List<T> printables) {
        printTable(printables, new String[]{});
    }

    /**
     * Prints a collection of objects as a formatted table with column selection.
     *
     * <p>Uses automatic Jackson-based object-to-map conversion and default formatting
     * options, but allows specification of which columns to display. This is useful
     * when you want to show only specific properties of your objects.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * List<User> users = userService.getAllUsers();
     * printer.printTable(users, "username", "email", "lastLogin");
     * }</pre>
     *
     * @param <T>        the type of objects to print
     * @param printables the collection of objects to display; {@code null} collections are ignored
     * @param columns    column names to display; if empty, all columns are shown
     * @see #printTable(Map, List, String...)
     */
    public <T> void printTable(List<T> printables, String... columns) {
        printTable(DEFAULT_TABLE_OPTIONS, printables, columns);
    }

    /**
     * Prints a collection of objects as a formatted table with custom options and column selection.
     *
     * <p>Uses automatic Jackson-based object-to-map conversion but allows customization of
     * formatting options and column selection. This method provides a balance between
     * convenience and customization.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * Map<String, Object> options = Map.of("maxColumnWidth", 30, "truncate", true);
     * List<Product> products = productService.getProducts();
     * printer.printTable(options, products, "name", "price", "category");
     * }</pre>
     *
     * @param <T>        the type of objects to print
     * @param options    formatting options to apply; these are merged with default options
     * @param printables the collection of objects to display; {@code null} collections are ignored
     * @param columns    column names to display; if empty, all columns are shown
     * @see #printTable(Map, List, Function, String...)
     */
    public <T> void printTable(Map<String, Object> options, List<T> printables, String... columns) {
        printTable(options, printables, this::convertToMap, columns);
    }

    /**
     * Prints a collection of objects as a formatted table with full customization options.
     *
     * <p>This is the most flexible table printing method, allowing complete control over
     * formatting options, object-to-map conversion, and column selection. Use this method
     * when you need custom object conversion logic or advanced formatting control.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * Map<String, Object> options = Map.of("rowHighlight", "ODD", "border", true);
     * Function<Person, Map<String, Object>> customMapper = person -> Map.of(
     *     "fullName", person.getFirstName() + " " + person.getLastName(),
     *     "ageGroup", person.getAge() < 30 ? "Young" : "Adult"
     * );
     * printer.printTable(options, people, customMapper, "fullName", "ageGroup");
     * }</pre>
     *
     * @param <T>            the type of objects to print
     * @param options        formatting options to apply; these are merged with default options
     * @param objectsToPrint the collection of objects to display; {@code null} collections are ignored
     * @param toMap          function to convert each object to a Map for table display
     * @param columns        column names to display; if empty, all columns are shown
     * @throws IllegalArgumentException if the conversion function fails for any object
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

    // Object Display Methods

    /**
     * Prints an object as a structured map display.
     *
     * <p>This method converts the object to a map representation using Jackson and
     * displays it in a structured format. It's particularly useful for displaying
     * single objects or detailed object information.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * User currentUser = userService.getCurrentUser();
     * printer.printAsMap(currentUser);
     * }</pre>
     *
     * @param <T>    the type of object to print
     * @param object the object to display; {@code null} objects are ignored
     * @see #convertToMap(Object)
     */
    public <T> void printAsMap(T object) {
        printAsMap(object, new String[]{});
    }

    /**
     * Prints an object as a formatted key-value map to the terminal.
     *
     * <p>This method converts any Java object into a map representation using Jackson's
     * ObjectMapper and displays it in a formatted key-value layout. The method supports
     * selective field display by allowing specification of which object properties to include
     * in the output.</p>
     *
     * <p>The display format presents each property as a key-value pair, making it ideal
     * for showing detailed object information in a readable format. This is particularly
     * useful for displaying configuration details, object summaries, or debug information.</p>
     *
     * <p>Key features:</p>
     * <ul>
     *   <li>Automatic object-to-map conversion using Jackson</li>
     *   <li>Optional field filtering to show only specified properties</li>
     *   <li>Graceful handling of null objects (silently ignored)</li>
     *   <li>Error handling with user-friendly error messages</li>
     * </ul>
     *
     * <p>Example usage:</p>
     * <pre>
     * // Print all object properties
     * printer.printAsMap(myObject);
     *
     * // Print only specific properties
     * printer.printAsMap(myObject, "name", "status", "lastModified");
     * </pre>
     *
     * <p>Example output format:</p>
     * <pre>
     * name         : MyService
     * status       : active
     * lastModified : 2025-06-26T10:30:00
     * </pre>
     *
     * @param <T> the type of object to print
     * @param object the object to display as a map; if null, the method returns silently
     * @param keys optional array of property names to include in the output; if not provided
     *            or empty, all object properties will be displayed. Keys are case-sensitive
     *            and must match the exact property names of the object
     * @throws IllegalArgumentException if the object cannot be converted to a Map representation
     * @see #convertToMap(Object)
     * @see #println(Map, Object)
     * @see #printErrorMessage(String)
     */
    public <T> void printAsMap(T object, String... keys) {
        if (object == null) {
            return;
        }

        Map<String, Object> map = convertToMap(object);
        if(keys != null && keys.length > 0) {
            Set<String> keySet = Set.of(keys);
            map = map.entrySet()
                    .stream()
                    .filter(entry -> keySet.contains(entry.getKey()))
                    .collect(
                    toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        try {
            println(DEFAULT_MAP_OPTIONS, map);
        } catch (Exception e) {
            printErrorMessage("Failed to print object: " + e.getMessage());
        }
    }


    // Styled Message Methods

    /**
     * Prints an error message with red bold styling.
     *
     * <p>Use this method for critical errors, failures, or any message that indicates
     * a problem that requires immediate attention.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * printer.printErrorMessage("Failed to connect to database: Connection timeout");
     * }</pre>
     *
     * @param message the error message to display; {@code null} messages are ignored
     * @see #ERROR_STYLE
     */
    public void printErrorMessage(String message) {
        if (message != null) {
            printStyledMessage(message, ERROR_STYLE);
        }
    }

    /**
     * Prints a warning message with yellow styling.
     *
     * <p>Use this method for warnings, deprecated features, or situations that might
     * require attention but are not critical errors.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * printer.printWarningMessage("API key will expire in 7 days");
     * }</pre>
     *
     * @param message the warning message to display; {@code null} messages are ignored
     * @see #WARNING_STYLE
     */
    public void printWarningMessage(String message) {
        if (message != null) {
            printStyledMessage(message, WARNING_STYLE);
        }
    }

    /**
     * Prints an informational message with blue styling.
     *
     * <p>Use this method for general information, status updates, or helpful hints
     * that enhance the user experience.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * printer.printInfoMessage("Processing 150 records...");
     * }</pre>
     *
     * @param message the informational message to display; {@code null} messages are ignored
     * @see #INFO_STYLE
     */
    public void printInfoMessage(String message) {
        if (message != null) {
            printStyledMessage(message, INFO_STYLE);
        }
    }

    /**
     * Prints a success message with green bold styling.
     *
     * <p>Use this method for successful operations, completions, or any positive
     * feedback that indicates successful execution.</p>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * printer.printSuccessMessage("Configuration updated successfully");
     * }</pre>
     *
     * @param message the success message to display; {@code null} messages are ignored
     * @see #SUCCESS_STYLE
     */
    public void printSuccessMessage(String message) {
        if (message != null) {
            printStyledMessage(message, SUCCESS_STYLE);
        }
    }

    // Exception Handling

    /**
     * Highlights and prints exception information to the terminal.
     *
     * <p>The display format is controlled by the "exception" option in the provided options map:</p>
     * <ul>
     * <li><strong>"stack"</strong> (default): Prints the full stack trace to stderr</li>
     * <li><strong>Any other value</strong>: Prints only the exception message with emphasis styling</li>
     * </ul>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * Map<String, Object> options = Map.of("exception", "message");
     * printer.highlightAndPrint(options, new RuntimeException("Something went wrong"));
     * }</pre>
     *
     * @param options   formatting options; may contain an "exception" key to control display format
     * @param exception the exception to display; {@code null} exceptions are ignored
     */
    @Override
    protected void highlightAndPrint(Map<String, Object> options, Throwable exception) {
        if (exception == null) {
            return;
        }

        String exceptionMode = (String) options.getOrDefault("exception", "stack");

        if ("stack".equals(exceptionMode)) {
            exception.printStackTrace();
        } else {
            AttributedStringBuilder builder = new AttributedStringBuilder();
            builder.append(exception.getMessage(), Styles.prntStyle().resolve(".em"));
            builder.toAttributedString().println(terminal());
        }
    }

    // Private Helper Methods

    /**
     * Converts an object to a Map representation using Jackson's ObjectMapper.
     *
     * <p>This method is used internally to transform Java objects into a format
     * suitable for table display. The conversion preserves object properties as
     * key-value pairs in the resulting map, handling nested objects and collections
     * according to Jackson's default serialization rules.</p>
     *
     * @param obj the object to convert; {@code null} objects return empty maps
     * @return a Map representation of the object's properties, never {@code null}
     * @throws IllegalArgumentException if the object cannot be converted to a Map
     */
    private Map<String, Object> convertToMap(Object obj) {
        if (obj == null) {
            return Map.of();
        }

        try {
            return objectMapper.convertValue(obj, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert object to map: " + e.getMessage(), e);
        }
    }

    /**
     * Prints a message with the specified styling to the terminal.
     *
     * <p>This method handles the low-level details of applying styles and ensuring
     * proper terminal output with flushing.</p>
     *
     * @param message the message to print; assumed to be non-null
     * @param style   the AttributedStyle to apply to the message
     */
    private void printStyledMessage(String message, AttributedStyle style) {
        AttributedString styledMessage = new AttributedStringBuilder()
                .style(style)
                .append(message)
                .toAttributedString();

        terminal.writer().println(styledMessage);
        terminal.flush();
    }

    /**
     * Creates a merged options map by combining default options with user-provided options.
     *
     * <p>User-provided options take precedence over default options.</p>
     *
     * @param customOptions the custom options to merge; may be {@code null}
     * @return a new map containing merged options, never {@code null}
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
     * @param <T>     the type of the object
     * @param object  the object to validate
     * @param message the error message if the object is null
     * @return the object if it's not null
     * @throws IllegalArgumentException if the object is null
     */
    private static <T> T validateNotNull(T object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
        return object;
    }
}