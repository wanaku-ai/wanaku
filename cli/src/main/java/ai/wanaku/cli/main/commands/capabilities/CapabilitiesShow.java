package ai.wanaku.cli.main.commands.capabilities;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.CapabilitiesHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.CapabilitiesService;
import org.jline.consoleui.prompt.ConsolePrompt;
import org.jline.consoleui.prompt.PromptResultItemIF;
import org.jline.consoleui.prompt.builder.ListPromptBuilder;
import org.jline.consoleui.prompt.builder.PromptBuilder;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static ai.wanaku.cli.main.support.CapabilitiesHelper.API_TIMEOUT;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.fetchAndMergeCapabilities;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.printCapability;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

/**
 * Command implementation for displaying detailed information about specific service capabilities.
 *
 * <p>This command retrieves and displays comprehensive details about capabilities for a specified
 * service. When multiple capabilities exist for the same service, it provides an interactive
 * selection interface allowing users to choose which specific capability instance to examine.</p>
 *
 * <p>The command supports filtering capabilities by service name and handles various scenarios:</p>
 * <ul>
 *   <li>No capabilities found - displays warning message</li>
 *   <li>Single capability - directly shows detailed information</li>
 *   <li>Multiple capabilities - presents interactive selection menu</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * wanaku capabilities show http
 * wanaku capabilities show --host http://remote:8080 sqs
 * }</pre>
 *
 * @author Wanaku CLI Team
 * @version 1.0
 * @since 1.0
 */
@Command(name = "show", description = "Show detailed information about a specific capability")
public class CapabilitiesShow extends BaseCommand {

    /**
     * Default API host URL used when no host is explicitly specified.
     */
    private static final String DEFAULT_HOST = "http://localhost:8080";

    /**
     * Format string for displaying capability choices in the selection menu.
     * Displays service type, host, port, status, and last seen timestamp.
     */
    private static final String CAPABILITY_CHOICE_FORMAT = "%-20s  %-20s  %-5d  %-10s  %-45s";

    /**
     * Prompt name identifier used for the interactive capability selection.
     */
    private static final String SELECTION_PROMPT_NAME = "index";

    /**
     * The API host URL for connecting to the targets service.
     * Defaults to localhost:8080 if not specified.
     */
    @Option(
            names = {"--host"},
            description = "The API host URL (default: " + DEFAULT_HOST + ")",
            defaultValue = DEFAULT_HOST
    )
    private String host;

    /**
     * The service name to show capability details for.
     * Must be exactly one service name (e.g., "http", "sqs", "file").
     */
    @Parameters(
            description = "The service name to show details for (e.g., http, sqs, file)",
            arity = "1..1"
    )
    private String service;

    /**
     * Service instance for interacting with the targets API.
     * Initialized during command execution with the specified host configuration.
     */
    private CapabilitiesService capabilitiesService;

    /**
     * Executes the capabilities show command.
     *
     * <p>This method orchestrates the entire capability retrieval and display process:</p>
     * <ol>
     *   <li>Initializes the targets service with the specified host</li>
     *   <li>Fetches all available capabilities from the API</li>
     *   <li>Filters capabilities by the specified service name</li>
     *   <li>Handles display based on the number of matching capabilities found</li>
     * </ol>
     *
     * @param terminal the terminal instance for user interaction
     * @param printer the printer instance for formatted output
     * @return {@link #EXIT_OK} on successful execution, {@link #EXIT_ERROR} on failure
     * @throws IOException if an I/O error occurs during API communication or terminal interaction
     * @throws Exception if any other error occurs during command execution
     */
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        capabilitiesService = initService(CapabilitiesService.class, host);

        // Fetch and filter capabilities by service name
        List<CapabilitiesHelper.PrintableCapability> capabilities = fetchAndMergeCapabilities(capabilitiesService)
                .await()
                .atMost(API_TIMEOUT)
                .stream()
                .filter(capability -> capability.service().equals(service))
                .toList();

        // Handle different capability count scenarios using enhanced switch expression
        return switch (capabilities.size()) {
            case 0 -> handleNoCapabilities(printer);
            case 1 -> handleSingleCapability(printer, capabilities.getFirst());
            default -> handleMultipleCapabilities(terminal, printer, capabilities);
        };
    }

    /**
     * Handles the scenario where no capabilities are found for the specified service.
     *
     * @param printer the printer instance for formatted output
     * @return {@link #EXIT_ERROR} to indicate no capabilities were found
     * @throws IOException if an error occurs while printing the warning message
     */
    private Integer handleNoCapabilities(WanakuPrinter printer) throws IOException {
        printer.printWarningMessage("No capabilities found for service: " + service);
        return EXIT_ERROR;
    }

    /**
     * Handles the scenario where exactly one capability is found for the specified service.
     * Directly displays the capability details without requiring user selection.
     *
     * @param printer the printer instance for formatted output
     * @param capability the single capability to display
     * @return {@link #EXIT_OK} on successful display
     * @throws Exception if an error occurs while printing capability details
     */
    private Integer handleSingleCapability(WanakuPrinter printer,
                                           CapabilitiesHelper.PrintableCapability capability) throws Exception {
        printCapabilityDetails(printer, capability);
        return EXIT_OK;
    }

    /**
     * Handles the scenario where multiple capabilities are found for the specified service.
     *
     * <p>Creates an interactive selection menu allowing the user to choose which specific
     * capability instance to examine. The menu displays key information about each capability
     * including service type, host, port, status, and last seen timestamp.</p>
     *
     * @param terminal the terminal instance for user interaction
     * @param printer the printer instance for formatted output
     * @param capabilities the list of capabilities to choose from
     * @return {@link #EXIT_OK} on successful selection and display, {@link #EXIT_ERROR} on failure
     * @throws Exception if an error occurs during user interaction or capability display
     */
    private Integer handleMultipleCapabilities(Terminal terminal,
                                               WanakuPrinter printer,
                                               List<CapabilitiesHelper.PrintableCapability> capabilities)
            throws Exception {

        printer.printWarningMessage("Multiple capabilities found for the " + service +
                " service. Please choose one.");

        ConsolePrompt.UiConfig uiConfig = new ConsolePrompt.UiConfig("=> ", "[]", "[x]", "-");
        ConsolePrompt prompt = new ConsolePrompt(terminal, uiConfig);

        PromptBuilder builder = prompt.getPromptBuilder();

        // Create interactive selection prompt
        ListPromptBuilder listPromptBuilder = builder.createListPrompt()
                .name(SELECTION_PROMPT_NAME)
                .message("Select a capability instance:");

        // Add each capability as a selectable option with formatted display text
        for (int i = 0; i < capabilities.size(); i++) {
            CapabilitiesHelper.PrintableCapability capability = capabilities.get(i);
            String displayText = formatCapabilityChoice(capability);

            listPromptBuilder
                    .newItem(String.valueOf(i))
                    .text(displayText)
                    .add();
        }
        listPromptBuilder.addPrompt();

        try {
            Map<String, PromptResultItemIF> result = prompt.prompt(builder.build());
            int selectedIndex = Integer.parseInt(result.get(SELECTION_PROMPT_NAME).getResult());
            printCapabilityDetails(printer, capabilities.get(selectedIndex));
            return EXIT_OK;
        } catch (Exception e) {
            printer.printErrorMessage("Error during capability selection: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    /**
     * Formats a capability for display in the selection menu.
     *
     * @param capability the capability to format
     * @return formatted string containing service type, host, port, status, and last seen information
     */
    private String formatCapabilityChoice(CapabilitiesHelper.PrintableCapability capability) {
        return String.format(CAPABILITY_CHOICE_FORMAT,
                capability.serviceType(),
                capability.host(),
                capability.port(),
                capability.status(),
                capability.lastSeen());
    }

    /**
     * Prints comprehensive details for a selected capability.
     *
     * <p>Displays both the basic capability information and its associated configurations
     * in a formatted table structure.</p>
     *
     * @param printer the printer instance for formatted output
     * @param capability the capability whose details should be printed
     * @throws Exception if an error occurs while printing the capability details or configurations
     */
    private void printCapabilityDetails(WanakuPrinter printer,
                                        CapabilitiesHelper.PrintableCapability capability) throws Exception {
        printer.printInfoMessage("Capability Details:");
        printCapability(capability, printer);

        printer.printInfoMessage("\nConfigurations:");
        printer.printTable(capability.configurations(), "name", "description");
    }
}