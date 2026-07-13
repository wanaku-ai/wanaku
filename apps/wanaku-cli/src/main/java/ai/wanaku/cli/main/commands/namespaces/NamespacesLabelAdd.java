package ai.wanaku.cli.main.commands.namespaces;

import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.IdSelector;
import ai.wanaku.cli.main.support.LabelHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.NamespacesService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;

/**
 * CLI command for adding labels to existing namespaces.
 * <p>
 * This command allows you to add one or more labels to a namespace without modifying
 * its other properties. If a label key already exists, its value will be updated.
 * </p>
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * # Add a single label
 * wanaku namespaces label add --id namespace-id-here --label env=production
 *
 * # Add multiple labels
 * wanaku namespaces label add --id namespace-id-here -l env=production -l tier=backend -l version=2.0
 *
 * # Add labels using label expression to select namespaces
 * wanaku namespaces label add --label-expression 'category=internal' --label migrated=true
 * </pre>
 *
 * @see NamespacesLabelRemove
 * @see NamespacesLabel
 */
@CommandLine.Command(name = "add", description = "Add labels to namespaces")
public class NamespacesLabelAdd extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    IdSelector selector;

    @CommandLine.Option(
            names = {"-l", "--label"},
            description = "Label to add in key=value format (can be specified multiple times)",
            required = true,
            arity = "1..*")
    List<String> labels;

    NamespacesService namespacesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (namespacesService == null) {
            namespacesService = initAuthenticatedService(NamespacesService.class, host);
        }

        Map<String, String> labelsToAdd = LabelHelper.parseLabels(labels, printer);
        if (labelsToAdd == null) {
            return EXIT_ERROR;
        }

        if (selector.labelExpression != null) {
            return addLabelsByExpression(labelsToAdd, printer);
        }

        return addLabelsById(labelsToAdd, printer);
    }

    private Integer addLabelsById(Map<String, String> labelsToAdd, WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<Namespace> response = namespacesService.getById(selector.id);
            Namespace namespace = response.data();

            if (namespace == null) {
                printer.printErrorMessage(String.format("Namespace with ID '%s' not found", selector.id));
                return EXIT_ERROR;
            }

            return LabelHelper.addLabelsToEntity(
                    namespace,
                    labelsToAdd,
                    printer,
                    n -> namespacesService.update(selector.id, n),
                    "namespace with ID",
                    selector.id);
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Namespace", selector.id, printer);
        }
    }

    private Integer addLabelsByExpression(Map<String, String> labelsToAdd, WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<List<Namespace>> response = namespacesService.list(selector.labelExpression);
            return LabelHelper.addLabelsByExpression(
                    response,
                    labelsToAdd,
                    printer,
                    n -> namespacesService.update(n.getId(), n),
                    Namespace::getId,
                    "namespace(s)",
                    selector.labelExpression);
        } catch (WebApplicationException ex) {
            commonResponseErrorHandler(ex.getResponse());
            return EXIT_ERROR;
        }
    }
}
