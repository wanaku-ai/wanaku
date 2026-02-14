// CapabilitiesList.java
package ai.wanaku.cli.main.commands.capabilities;

import java.io.IOException;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.CapabilitiesService;

import static ai.wanaku.cli.main.support.CapabilitiesHelper.API_TIMEOUT;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.fetchAndMergeCapabilities;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.printCapabilities;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

/**
 * Command-line interface for listing all service capabilities in the Wanaku system.
 *
 * <p>This command provides functionality to discover and display both management tools
 * and resource providers available in the system, along with their current status
 * and activity information. It combines data from multiple API endpoints to present
 * a unified view of system capabilities in a tabular format.</p>
 *
 * <p>The command supports the following functionality:</p>
 * <ul>
 *   <li>Fetches management tools and resource providers from the API</li>
 *   <li>Retrieves activity status for each service</li>
 *   <li>Merges and correlates data from multiple sources</li>
 *   <li>Displays results in a formatted table with columns for service, type, host, port, status, and last seen</li>
 * </ul>
 *
 * <p>Usage examples:</p>
 * <pre>
 * # List all capabilities using default host
 * wanaku capabilities list
 *
 * # List capabilities from a specific host
 * wanaku capabilities list --host http://api.example.com:8080
 * </pre>
 *
 * <p>Output format:</p>
 * <pre>
 * service | serviceType       | host          | port | status | lastSeen
 * --------|-------------------|---------------|------|--------|----------
 * http    | tool-invoker      | 192.168.1.101 | 9000 | active | Monday, June 23, 2025 at 07:00:26
 * sqs     | tool-invoker      | 192.168.1.101 | 9011 | active | Monday, June 16, 2025 at 13:22:29
 * </pre>
 *
 * @see CapabilitiesShow
 * @see ai.wanaku.cli.main.support.CapabilitiesHelper
 */
@Command(name = "list", description = "List all available capabilities")
public class CapabilitiesList extends BaseCommand {

    /**
     * API host URL for connecting to the Wanaku services.
     *
     * <p>This option allows users to specify the base URL of the Wanaku API server.
     * The URL should include the protocol (http/https) and port if different from default.</p>
     *
     * <p>Examples of valid host URLs:</p>
     * <ul>
     *   <li>http://localhost:8080 (default)</li>
     *   <li>https://api.wanaku.ai</li>
     *   <li>http://192.168.1.100:9090</li>
     * </ul>
     */
    @Option(
            names = {"--host"},
            description = "The API host URL (default: http://localhost:8080)",
            defaultValue = "http://localhost:8080")
    private String host;

    @Option(
            names = {"-e", "--label-expression"},
            description = {
                """
Filter capabilities by label expression. Supports logical operators for complex queries.
For detailed information see the label expression manual page:
`wanaku man label-expression`
Note: If omitted, all capabilities are listed. Label matching is case-sensitive.
"""
            })
    private String labelExpression;

    /**
     * REST client for communicating with the targets service API.
     * Initialized during command execution.
     */
    private CapabilitiesService capabilitiesService;

    /**
     * Executes the capabilities listing command.
     *
     * <p>This method orchestrates the entire process of:</p>
     * <ol>
     *   <li>Creating a terminal instance for output</li>
     *   <li>Initializing the targets service with the specified host</li>
     *   <li>Fetching and merging capabilities data from the API</li>
     *   <li>Displaying the results in a formatted table</li>
     * </ol>
     *
     * <p>The method uses a try-with-resources block to ensure proper terminal cleanup
     * and includes timeout handling for API operations.</p>
     *
     * @return {@link BaseCommand#EXIT_OK} if the command executes successfully
     * @throws Exception if there's an error during execution, including:
     *                  <ul>
     *                    <li>API communication failures</li>
     *                    <li>Terminal creation issues</li>
     *                    <li>Data processing errors</li>
     *                    <li>Timeout while waiting for API responses</li>
     *                  </ul>
     */
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {

        capabilitiesService = initService(CapabilitiesService.class, host);
        var capabilities = fetchAndMergeCapabilities(capabilitiesService, labelExpression)
                .await()
                .atMost(API_TIMEOUT);
        printCapabilities(capabilities, printer);

        return EXIT_OK;
    }
}
