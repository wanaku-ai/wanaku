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
import ai.wanaku.cli.main.support.NameSelector;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;

/**
 * CLI command for removing tool references from the Wanaku router.
 * <p>
 * This command supports two removal modes:
 * </p>
 * <ul>
 * <li><b>Single removal by name:</b> Remove a specific tool using {@code --name}
 * <pre>wanaku tools remove --name my-tool</pre>
 * </li>
 * <li><b>Batch removal by label expression:</b> Remove multiple tools matching a label filter
 * <pre>wanaku tools remove --label-expression 'category=weather'</pre>
 * <pre>wanaku tools remove -l 'environment!=production' -y</pre>
 * </li>
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
 * <li>Mutual exclusivity between {@code --name} and {@code --label-expression}</li>
 * <li>Preview table showing tools before batch removal</li>
 * <li>Confirmation prompt (can be bypassed with {@code --assume-yes})</li>
 * <li>Clear feedback on removal results</li>
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

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    NameSelector selector;

    @CommandLine.Option(
            names = {"-y", "--assume-yes"},
            description = "automatically answer yes for all questions")
    private boolean assumeYes;

    ToolsService toolsService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        toolsService = initAuthenticatedService(ToolsService.class, host);

        if (selector.labelExpression != null) {
            return removeByLabelExpression(terminal, printer);
        }

        return removeByName(printer);
    }

    private Integer removeByName(WanakuPrinter printer) throws IOException {
        try {
            toolsService.remove(selector.name);
            printer.printSuccessMessage("Successfully removed tool reference '" + selector.name + "'");
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Tool", selector.name, printer);
        }
        return EXIT_OK;
    }

    private Integer removeByLabelExpression(Terminal terminal, WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<List<ToolReference>> response = toolsService.list(selector.labelExpression);
            List<ToolReference> matchingTools = response.data();

            if (matchingTools == null || matchingTools.isEmpty()) {
                printer.printWarningMessage("No tools found matching label expression: " + selector.labelExpression);
                return EXIT_OK;
            }

            printer.printInfoMessage(String.format(
                    "Found %d tool(s) matching label expression '%s'", matchingTools.size(), selector.labelExpression));

            printer.printTable(matchingTools, "name", "type", "uri", "labels");

            boolean continues = true;

            if (!assumeYes) {
                continues = CommandHelper.confirm(terminal, "Do you want to remove all the tools above?");
            }

            int removed = 0;

            if (continues) {
                removed = toolsService.removeIf(selector.labelExpression).data();
            }

            printer.printInfoMessage(String.format("Removal complete: %d tools removed", removed));

            return EXIT_OK;
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
    }
}
