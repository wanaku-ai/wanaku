package ai.wanaku.cli.main.commands.namespaces;

import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.util.List;
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
 * CLI command for removing labels from existing namespaces.
 * <p>
 * This command allows you to remove one or more labels from a namespace without affecting
 * its other properties. Labels that don't exist are silently ignored.
 * </p>
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * # Remove a single label
 * wanaku namespaces label remove --id namespace-id-here --label env
 *
 * # Remove multiple labels
 * wanaku namespaces label remove --id namespace-id-here -l env -l tier -l version
 *
 * # Remove labels from namespaces matching label expression
 * wanaku namespaces label remove --label-expression 'category=internal' --label temp
 * </pre>
 *
 * @see NamespacesLabelAdd
 * @see NamespacesLabel
 */
@CommandLine.Command(name = "remove", description = "Remove labels from namespaces")
public class NamespacesLabelRemove extends BaseCommand {

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
            description = "Label key to remove (can be specified multiple times)",
            required = true,
            arity = "1..*")
    List<String> labels;

    NamespacesService namespacesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (namespacesService == null) {
            namespacesService = initAuthenticatedService(NamespacesService.class, host);
        }

        if (selector.labelExpression != null) {
            return removeLabelsByExpression(printer);
        }

        return removeLabelsById(printer);
    }

    private Integer removeLabelsById(WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<Namespace> response = namespacesService.getById(selector.id);
            Namespace namespace = response.data();

            if (namespace == null) {
                printer.printErrorMessage(String.format("Namespace with ID '%s' not found", selector.id));
                return EXIT_ERROR;
            }

            return LabelHelper.removeLabelsFromEntity(
                    namespace,
                    labels,
                    printer,
                    n -> namespacesService.update(selector.id, n),
                    "namespace with ID",
                    selector.id);
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Namespace", selector.id, printer);
        }
    }

    private Integer removeLabelsByExpression(WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<List<Namespace>> response = namespacesService.list(selector.labelExpression);
            return LabelHelper.removeLabelsByExpression(
                    response,
                    labels,
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
