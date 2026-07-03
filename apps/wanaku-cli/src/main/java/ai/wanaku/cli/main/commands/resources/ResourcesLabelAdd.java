package ai.wanaku.cli.main.commands.resources;

import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.LabelHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ResourcesService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;

/**
 * CLI command for adding labels to existing resources.
 * <p>
 * This command allows you to add one or more labels to a resource without modifying
 * its other properties. If a label key already exists, its value will be updated.
 * </p>
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * # Add a single label
 * wanaku resources label add --name my-resource --label env=production
 *
 * # Add multiple labels
 * wanaku resources label add --name my-resource -l env=production -l tier=backend -l version=2.0
 *
 * # Add labels using label expression to select resources
 * wanaku resources label add --label-expression 'category=data' --label migrated=true
 * </pre>
 *
 * @see ResourcesLabelRemove
 * @see ResourcesLabel
 */
@CommandLine.Command(name = "add", description = "Add labels to resources")
public class ResourcesLabelAdd extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"-n", "--name"},
            description = "Name of the resource to add labels to. Cannot be used with --label-expression.")
    String name;

    @CommandLine.Option(
            names = {"-l", "--label"},
            description = "Label to add in key=value format (can be specified multiple times)",
            required = true,
            arity = "1..*")
    List<String> labels;

    @CommandLine.Option(
            names = {"-e", "--label-expression"},
            description = "Add labels to all resources matching this label expression. Cannot be used with --name.")
    String labelExpression;

    ResourcesService resourcesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (resourcesService == null) {
            resourcesService = initAuthenticatedService(ResourcesService.class, host);
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
            WanakuResponse<ResourceReference> response = resourcesService.getByName(name);
            ResourceReference resource = response.data();

            if (resource == null) {
                printer.printErrorMessage(String.format("Resource '%s' not found", name));
                return EXIT_ERROR;
            }

            return LabelHelper.addLabelsToEntity(
                    resource, labelsToAdd, printer, r -> resourcesService.update(r.getName(), r), "resource", name);
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Resource", name, printer);
        }
    }

    private Integer addLabelsByExpression(Map<String, String> labelsToAdd, WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<List<ResourceReference>> response = resourcesService.list(labelExpression);
            return LabelHelper.addLabelsByExpression(
                    response,
                    labelsToAdd,
                    printer,
                    r -> resourcesService.update(r.getName(), r),
                    ResourceReference::getName,
                    "resource(s)",
                    labelExpression);
        } catch (WebApplicationException ex) {
            commonResponseErrorHandler(ex.getResponse());
            return EXIT_ERROR;
        }
    }
}
