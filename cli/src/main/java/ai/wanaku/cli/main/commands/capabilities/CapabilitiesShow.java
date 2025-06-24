package ai.wanaku.cli.main.commands.capabilities;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.CapabilitiesHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.TargetsService;
import org.jline.consoleui.prompt.ConsolePrompt;
import org.jline.consoleui.prompt.PromptResultItemIF;
import org.jline.consoleui.prompt.builder.ListPromptBuilder;
import org.jline.consoleui.prompt.builder.PromptBuilder;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

import java.util.List;
import java.util.Map;

import static ai.wanaku.cli.main.support.CapabilitiesHelper.API_TIMEOUT;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.fetchAndMergeCapabilities;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.initializeTargetsService;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.printCapability;

/**
 * Command-line interface for displaying detailed information about a specific service capability.
 *
 * <p>This command allows users to view comprehensive details about a particular service in the
 * Wanaku system, including its configuration parameters, status, and connection information.
 * When multiple instances of the same service are found, it provides an interactive selection
 * interface to choose the specific instance to display.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Displays detailed information for a specific service type</li>
 *   <li>Shows service configurations and parameters</li>
 *   <li>Handles multiple service instances with interactive selection</li>
 *   <li>Provides user-friendly error messages for missing services</li>
 * </ul>
 *
 * <p>Usage examples:</p>
 * <pre>
 * # Show details for HTTP service
 * wanaku capabilities show http
 *
 * # Show details for SQS service from specific host
 * wanaku capabilities show sqs --host http://api.example.com:8080
 * </pre>
 *
 * <p>Output includes:</p>
 * <ul>
 *   <li>Service summary table (service, type, host, port, status, lastSeen)</li>
 *   <li>Configuration details table (name, description)</li>
 * </ul>
 *
 * @author Wanaku CLI Team
 * @since 1.0
 * @see ai.wanaku.cli.main.commands.capabilities.CapabilitiesList
 * @see CapabilitiesHelper
 */
@CommandLine.Command(name = "show", description = "Show detailed information about a specific capability")
public class CapabilitiesShow extends BaseCommand {

    /**
     * API host URL for connecting to the Wanaku services.
     *
     * <p>This option allows users to specify the base URL of the Wanaku API server.
     * The URL should include the protocol (http/https) and port if different from default.</p>
     *
     * @see CapabilitiesList#host for more details on valid host formats
     */
    @CommandLine.Option(
            names = {"--host"},
            description = "The API host URL (default: http://localhost:8080)",
            defaultValue = "http://localhost:8080"
    )
    private String host;

    /**
     * The service name to display details for.
     *
     * <p>This parameter specifies which service type to show detailed information about.</p>
     *
     * <p>The service name is case-sensitive and must match exactly with the service
     * types returned by the API.</p>
     */
    @CommandLine.Parameters(
            description = "The service name to show details for (e.g., http, sqs, file)",
            arity = "1..1")
    private String service;

    /**
     * REST client for communicating with the targets service API.
     * Initialized during command execution.
     */
    private TargetsService targetsService;

    /**
     * Printer instance for formatted output display.
     * Initialized during command execution.
     */
    private WanakuPrinter printer;

    /**
     * Executes the capability show command.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Initializes the targets service and terminal</li>
     *   <li>Fetches all capabilities and filters by the specified service</li>
     *   <li>Handles different scenarios based on the number of matching capabilities:
     *       <ul>
     *         <li>No matches: Displays warning and exits with error</li>
     *         <li>Single match: Displays capability details directly</li>
     *         <li>Multiple matches: Prompts user to select specific instance</li>
     *       </ul>
     *   </li>
     *   <li>Displays detailed capability information including configurations</li>
     * </ol>
     *
     * @return {@link BaseCommand#EXIT_OK} if successful, {@link BaseCommand#EXIT_ERROR} if no capabilities found
     * @throws Exception if there's an error during execution, including:
     *                  <ul>
     *                    <li>API communication failures</li>
     *                    <li>Terminal creation issues</li>
     *                    <li>User interaction errors</li>
     *                    <li>Data processing errors</li>
     *                  </ul>
     */
    @Override
    public Integer call() throws Exception {
        targetsService = initializeTargetsService(host);

        try (Terminal terminal = WanakuPrinter.terminalInstance()) {

            printer = new WanakuPrinter(null, terminal);

            // Fetch capabilities and filter by service name
            var capabilities = fetchAndMergeCapabilities(targetsService)
                    .await()
                    .atMost(API_TIMEOUT)
                    .stream()
                    .filter(capability -> capability.service().equals(service))
                    .toList();

            // Handle different capability count scenarios
            return switch (capabilities.size()) {
                case 0 -> handleNoCapabilities();
                case 1 -> handleSingleCapability(terminal, capabilities.get(0));
                default -> handleMultipleCapabilities(terminal, capabilities);
            };
        }
    }

    /**
     * Handles the case when no capabilities are found for the specified service.
     *
     * @return {@link BaseCommand#EXIT_ERROR} to indicate failure
     */
    private Integer handleNoCapabilities() {
        printer.printWarningMessage("No capabilities found for service: " + service);
        return EXIT_ERROR;
    }

    /**
     * Handles the case when exactly one capability is found for the specified service.
     *
     * @param terminal the terminal instance for output
     * @param capability the single capability to display
     * @return {@link BaseCommand#EXIT_OK} to indicate success
     * @throws Exception if there's an error displaying the capability details
     */
    private Integer handleSingleCapability(Terminal terminal, CapabilitiesHelper.PrintableCapability capability) throws Exception {
        printCapabilityDetails(terminal, capability);
        return EXIT_OK;
    }

    /**
     * Handles the case when multiple capabilities are found for the specified service.
     *
     * <p>This method presents an interactive selection interface allowing the user
     * to choose which specific capability instance to display details for.</p>
     *
     * @param terminal the terminal instance for output and user interaction
     * @param capabilities the list of capabilities to choose from
     * @return {@link BaseCommand#EXIT_OK} if successful, {@link BaseCommand#EXIT_ERROR} if selection fails
     * @throws Exception if there's an error during user interaction or display
     */
    private Integer handleMultipleCapabilities(Terminal terminal, List<CapabilitiesHelper.PrintableCapability> capabilities) throws Exception {
        printer.printWarningMessage("Multiple capabilities found for the " + service + " service. Please choose one.");

        String choicesFormat = "%-20s  %-20s  %-5d  %-10s  %-45s";
        ConsolePrompt prompt = new ConsolePrompt(terminal);
        PromptBuilder builder = prompt.getPromptBuilder();

        // Create interactive selection prompt
        ListPromptBuilder listPromptBuilder = builder.createListPrompt()
                .name("index")
                .message("Select a capability instance:");

        // Add each capability as a selectable option
        for (int i = 0; i < capabilities.size(); i++) {
            CapabilitiesHelper.PrintableCapability capability = capabilities.get(i);


            String displayText = String.format(choicesFormat,
                    capability.serviceType(),
                    capability.host(),
                    capability.port(),
                    capability.status(),
                    capability.lastSeen());

            listPromptBuilder
                    .newItem(String.valueOf(i))
                    .text(displayText)
                    .add();
        }
        listPromptBuilder.addPrompt();

        try {
            Map<String, PromptResultItemIF> result = prompt.prompt(builder.build());
            int selectedIndex = Integer.parseInt(result.get("index").getResult());
            printCapabilityDetails(terminal, capabilities.get(selectedIndex));
            return EXIT_OK;
        } catch (Exception e) {
            printer.printErrorMessage("Error during capability selection: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    /**
     * Displays detailed information about a specific capability.
     *
     * <p>This method outputs:</p>
     * <ul>
     *   <li>A summary table showing the capability's basic information</li>
     *   <li>A configurations table showing the capability's configuration parameters</li>
     * </ul>
     *
     * @param terminal the terminal instance for output
     * @param capability the capability to display details for
     * @throws Exception if there's an error during output formatting or display
     */
    private void printCapabilityDetails(Terminal terminal, CapabilitiesHelper.PrintableCapability capability) throws Exception {
        printer.printInfoMessage("Capability Details:");
        printCapability(capability, terminal);

        printer.printInfoMessage("\nConfigurations:");
        printer.printTable(capability.configurations(), "name", "description");
    }
}
