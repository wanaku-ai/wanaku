package ai.wanaku.cli.main.commands.resources;

import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.LabelHelper;
import ai.wanaku.cli.main.support.NameSelector;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ResourcesService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;

/**
 * CLI command for removing labels from existing resources.
 * <p>
 * This command allows you to remove one or more labels from a resource without modifying
 * its other properties. If a label key doesn't exist, it will be silently ignored.
 * </p>
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * # Remove a single label
 * wanaku resources label remove --name my-resource --label env
 *
 * # Remove multiple labels
 * wanaku resources label remove --name my-resource -l env -l tier -l version
 *
 * # Remove labels from resources matching a label expression
 * wanaku resources label remove --label-expression 'status=deprecated' --label temporary
 * </pre>
 *
 * @see ResourcesLabelAdd
 * @see ResourcesLabel
 */
@CommandLine.Command(name = "remove", description = "Remove labels from resources")
public class ResourcesLabelRemove extends BaseCommand {

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

    ResourcesService resourcesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (resourcesService == null) {
            resourcesService = initAuthenticatedService(ResourcesService.class, host);
        }

        if (selector.labelExpression != null) {
            return removeLabelsByExpression(printer);
        }

        return removeLabelsByName(printer);
    }

    private Integer removeLabelsByName(WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<ResourceReference> response = resourcesService.getByName(selector.name);
            ResourceReference resource = response.data();

            if (resource == null) {
                printer.printErrorMessage(String.format("Resource '%s' not found", selector.name));
                return EXIT_ERROR;
            }

            return LabelHelper.removeLabelsFromEntity(
                    resource,
                    labelKeys,
                    printer,
                    r -> resourcesService.update(r.getName(), r),
                    "resource",
                    selector.name);
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Resource", selector.name, printer);
        }
    }

    private Integer removeLabelsByExpression(WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<List<ResourceReference>> response = resourcesService.list(selector.labelExpression);
            return LabelHelper.removeLabelsByExpression(
                    response,
                    labelKeys,
                    printer,
                    r -> resourcesService.update(r.getName(), r),
                    ResourceReference::getName,
                    "resource(s)",
                    selector.labelExpression);
        } catch (WebApplicationException ex) {
            commonResponseErrorHandler(ex.getResponse());
            return EXIT_ERROR;
        }
    }
}
