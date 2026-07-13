package ai.wanaku.cli.main.commands.tools;

import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.LabelHelper;
import ai.wanaku.cli.main.support.NameSelector;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;

/**
 * CLI command for removing labels from existing tools.
 * <p>
 * This command allows you to remove one or more labels from a tool without modifying
 * its other properties. If a label key doesn't exist, it will be silently ignored.
 * </p>
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * # Remove a single label
 * wanaku tools label remove --name my-tool --label env
 *
 * # Remove multiple labels
 * wanaku tools label remove --name my-tool -l env -l tier -l version
 *
 * # Remove labels from tools matching a label expression
 * wanaku tools label remove --label-expression 'status=deprecated' --label temporary
 * </pre>
 *
 * @see ToolsLabelAdd
 * @see ToolsLabel
 */
@CommandLine.Command(name = "remove", description = "Remove labels from tools")
public class ToolsLabelRemove extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    NameSelector selector;

    @CommandLine.Option(
            names = {"-l", "--label"},
            description = "Label key to remove (can be specified multiple times)",
            required = true,
            arity = "1..*")
    List<String> labelKeys;

    ToolsService toolsService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (toolsService == null) {
            toolsService = initAuthenticatedService(ToolsService.class, host);
        }

        if (selector.labelExpression != null) {
            return removeLabelsByExpression(printer);
        }

        return removeLabelsByName(printer);
    }

    private Integer removeLabelsByName(WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<ToolReference> response = toolsService.getByName(selector.name);
            ToolReference tool = response.data();

            if (tool == null) {
                printer.printErrorMessage(String.format("Tool '%s' not found", selector.name));
                return EXIT_ERROR;
            }

            return LabelHelper.removeLabelsFromEntity(
                    tool, labelKeys, printer, t -> toolsService.update(t.getName(), t), "tool", selector.name);
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Tool", selector.name, printer);
        }
    }

    private Integer removeLabelsByExpression(WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<List<ToolReference>> response = toolsService.list(selector.labelExpression);
            return LabelHelper.removeLabelsByExpression(
                    response,
                    labelKeys,
                    printer,
                    t -> toolsService.update(t.getName(), t),
                    ToolReference::getName,
                    "tool(s)",
                    selector.labelExpression);
        } catch (WebApplicationException ex) {
            commonResponseErrorHandler(ex.getResponse());
            return EXIT_ERROR;
        }
    }
}
