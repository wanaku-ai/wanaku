package ai.wanaku.cli.main.commands.tools;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.CommandHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

/**
 * CLI command for removing tool references from the Wanaku router.
 * <p>
 * This command supports two removal modes:
 * </p>
 * <ul>
 *   <li><b>Single removal by name:</b> Remove a specific tool using {@code --name}
 *       <pre>wanaku tools remove --name my-tool</pre>
 *   </li>
 *   <li><b>Batch removal by label expression:</b> Remove multiple tools matching a label filter
 *       <pre>wanaku tools remove --label-expression 'category=weather'</pre>
 *       <pre>wanaku tools remove -l 'environment!=production' -y</pre>
 *   </li>
 * </ul>
 * <p>
 * <b>Label Expression Syntax:</b>
 * </p>
 * <p>
 * The {@code --label-expression} option supports complex filtering using logical operators.
 * For detailed syntax and examples, see the label expression manual page:
 * </p>
 * <pre>wanaku man label-expression</pre>
 * <p>
 * <b>Safety Features:</b>
 * </p>
 * <ul>
 *   <li>Mutual exclusivity between {@code --name} and {@code --label-expression}</li>
 *   <li>Preview table showing tools before batch removal</li>
 *   <li>Confirmation prompt (can be bypassed with {@code --assume-yes})</li>
 *   <li>Clear feedback on removal results</li>
 * </ul>
 * <p>
 * <b>Warning:</b> Removal operations cannot be undone. Removed tools are unregistered
 * from the tool manager and deleted from the repository.
 * </p>
 *
 * @see ToolsService
 * @see ToolsAdd
 * @see ToolsList
 */
@CommandLine.Command(name = "remove", description = "Remove tools")
public class ToolsRemove extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"-n", "--name"},
            description = "Name of the tool to remove. Cannot be used with --label-expression.")
    private String name;

    @CommandLine.Option(
            names = {"-y", "--assume-yes"},
            description = "automatically answer yes for all questions")
    private boolean assumeYes;

    @CommandLine.Option(
            names = {"-e", "--label-expression"},
            description = {
                """
                    Remove tools matching a label filter expression. Supports logical operators for complex queries.",
                    For detailed information see the label expression manual page:
                    `wanaku man label-expression`
                    Note: Use --name to remove a single tool by name. The --label-expression,
                    option enables batch removal of multiple tools. Use with caution as this,
                    operation cannot be undone.
            """
            },
            arity = "0..1")
    private String labelExpression;

    ToolsService toolsService;

    /**
     * Executes the tool removal command.
     * <p>
     * This method validates the command-line arguments and delegates to either
     * {@link #removeByName(WanakuPrinter)} or {@link #removeByLabelExpression(Terminal, WanakuPrinter)}
     * depending on which option was provided.
     * </p>
     * <p>
     * The method enforces mutual exclusivity between {@code --name} and {@code --label-expression}
     * options, ensuring exactly one removal method is specified.
     * </p>
     *
     * @param terminal the terminal for user interaction
     * @param printer  the printer for displaying messages to the user
     * @return {@link #EXIT_OK} if removal succeeded, {@link #EXIT_ERROR} if it failed or
     *         validation errors occurred
     * @throws Exception if an unexpected error occurs during command execution
     */
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        toolsService = initService(ToolsService.class, host);

        // Validate that either name or labelExpression is provided, but not both
        if (name != null && labelExpression != null) {
            printer.printErrorMessage("Cannot specify both --name and --label-expression. Use one or the other.");
            return EXIT_ERROR;
        }

        if (name == null && labelExpression == null) {
            printer.printErrorMessage("Must specify either --name or --label-expression.");
            return EXIT_ERROR;
        }

        // Handle removal by label expression
        if (labelExpression != null) {
            return removeByLabelExpression(terminal, printer);
        }

        // Handle removal by name
        return removeByName(printer);
    }

    /**
     * Removes a single tool by its name.
     * <p>
     * This method sends a removal request to the tools service for the tool
     * identified by the {@code --name} option. It provides appropriate feedback
     * to the user based on the result:
     * </p>
     * <ul>
     *   <li>Success: Displays a success message with the tool name</li>
     *   <li>Not found: Displays a warning that the tool doesn't exist</li>
     *   <li>Other errors: Displays the error details</li>
     * </ul>
     *
     * @param printer the printer for displaying messages to the user
     * @return {@link #EXIT_OK} if the tool was successfully removed,
     *         {@link #EXIT_ERROR} if the tool was not found or removal failed
     * @throws IOException if an I/O error occurs during communication with the service
     */
    private Integer removeByName(WanakuPrinter printer) throws IOException {
        try (Response ignored = toolsService.remove(name)) {
            printer.printSuccessMessage("Successfully removed tool reference '" + name + "'");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                String warningMessage = String.format(
                        "Tool not found (%s): %s%n",
                        name, response.getStatusInfo().getReasonPhrase());
                printer.printWarningMessage(warningMessage);
            } else {
                commonResponseErrorHandler(response);
            }
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }

    /**
     * Removes multiple tools that match a label expression.
     * <p>
     * This method performs batch removal of tools based on the {@code --label-expression}
     * option. The removal process follows these steps:
     * </p>
     * <ol>
     *   <li>Query the service for all tools matching the label expression</li>
     *   <li>Display the count and details of matching tools in a table format</li>
     *   <li>Prompt the user for confirmation (unless {@code --assume-yes} is specified)</li>
     *   <li>If confirmed, invoke the batch removal operation on the service</li>
     *   <li>Display the final count of removed tools</li>
     * </ol>
     * <p>
     * If no tools match the expression, a warning is displayed and the operation
     * completes successfully without removing anything.
     * </p>
     * <p>
     * <b>Safety features:</b>
     * </p>
     * <ul>
     *   <li>Shows a preview table of tools before removal</li>
     *   <li>Requires user confirmation (can be skipped with {@code -y})</li>
     *   <li>Reports exact count of removed tools</li>
     * </ul>
     *
     * @param terminal the terminal for user interaction and confirmation prompts
     * @param printer  the printer for displaying messages and tables to the user
     * @return {@link #EXIT_OK} if the operation completed successfully (regardless of whether
     *         any tools were removed), {@link #EXIT_ERROR} if an error occurred during removal
     * @throws IOException if an I/O error occurs during communication with the service or
     *                     while reading user input
     * @see ToolsService#list(String)
     * @see ToolsService#removeIf(String)
     */
    private Integer removeByLabelExpression(Terminal terminal, WanakuPrinter printer) throws IOException {
        try {
            // Get all tools matching the label expression
            WanakuResponse<List<ToolReference>> response = toolsService.list(labelExpression);
            List<ToolReference> matchingTools = response.data();

            if (matchingTools == null || matchingTools.isEmpty()) {
                printer.printWarningMessage("No tools found matching label expression: " + labelExpression);
                return EXIT_OK;
            }

            printer.printInfoMessage(String.format(
                    "Found %d tool(s) matching label expression '%s'", matchingTools.size(), labelExpression));

            printer.printTable(matchingTools, "name", "type", "uri", "labels");

            boolean continues = true;

            if (!assumeYes) {
                continues = CommandHelper.confirm(terminal, "Do you want to remove all the tools above?");
            }

            int removed = 0;

            if (continues) {
                removed = toolsService.removeIf(labelExpression).data();
            }

            printer.printInfoMessage(String.format("Removal complete: %d tools removed", removed));

            return 0;
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
    }
}
