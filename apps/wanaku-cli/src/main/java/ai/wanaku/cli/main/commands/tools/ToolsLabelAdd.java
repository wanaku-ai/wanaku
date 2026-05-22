package ai.wanaku.cli.main.commands.tools;

import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.LabelHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;

/**
 * CLI command for adding labels to existing tools.
 * <p>
 * This command allows you to add one or more labels to a tool without modifying
 * its other properties. If a label key already exists, its value will be updated.
 * </p>
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * # Add a single label
 * wanaku tools label add --name my-tool --label env=production
 *
 * # Add multiple labels
 * wanaku tools label add --name my-tool -l env=production -l tier=backend -l version=2.0
 *
 * # Add labels using label expression to select tools
 * wanaku tools label add --label-expression 'category=weather' --label migrated=true
 * </pre>
 *
 * @see ToolsLabelRemove
 * @see ToolsLabel
 */
@CommandLine.Command(name = "add", description = "Add labels to tools")
public class ToolsLabelAdd extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"-n", "--name"},
            description = "Name of the tool to add labels to. Cannot be used with --label-expression.")
    String name;

    @CommandLine.Option(
            names = {"-l", "--label"},
            description = "Label to add in key=value format (can be specified multiple times)",
            required = true,
            arity = "1..*")
    List<String> labels;

    @CommandLine.Option(
            names = {"-e", "--label-expression"},
            description = "Add labels to all tools matching this label expression. Cannot be used with --name.")
    String labelExpression;

    ToolsService toolsService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (toolsService == null) {
            toolsService = initService(ToolsService.class, host);
        }

        int validationResult = LabelHelper.validateLabelExpression(name, labelExpression, "--name", printer);
        if (validationResult != EXIT_OK) {
            return validationResult;
        }

        Map<String, String> labelsToAdd = LabelHelper.parseLabels(labels, printer);
        if (labelsToAdd == null) {
            return EXIT_ERROR;
        }

        if (labelExpression != null) {
            return addLabelsByExpression(labelsToAdd, printer);
        }

        return addLabelsByName(labelsToAdd, printer);
    }

    private Integer addLabelsByName(Map<String, String> labelsToAdd, WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<ToolReference> response = toolsService.getByName(name);
            ToolReference tool = response.data();

            if (tool == null) {
                printer.printErrorMessage(String.format("Tool '%s' not found", name));
                return EXIT_ERROR;
            }

            return LabelHelper.addLabelsToEntity(
                    tool, labelsToAdd, printer, t -> toolsService.update(t.getName(), t), "tool", name);
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Tool", name, printer);
        }
    }

    private Integer addLabelsByExpression(Map<String, String> labelsToAdd, WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<List<ToolReference>> response = toolsService.list(labelExpression);
            return LabelHelper.addLabelsByExpression(
                    response,
                    labelsToAdd,
                    printer,
                    t -> toolsService.update(t.getName(), t),
                    ToolReference::getName,
                    "tool(s)",
                    labelExpression);
        } catch (WebApplicationException ex) {
            commonResponseErrorHandler(ex.getResponse());
            return EXIT_ERROR;
        }
    }
}
