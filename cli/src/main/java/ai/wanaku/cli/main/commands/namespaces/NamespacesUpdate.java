package ai.wanaku.cli.main.commands.namespaces;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.NamespacesService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

/**
 * CLI command for updating namespaces.
 * <p>
 * Updates merge with existing values so only provided fields are changed.
 * </p>
 */
@CommandLine.Command(name = "update", description = "Update an existing namespace")
public class NamespacesUpdate extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Parameters(description = "The ID of the namespace to update", arity = "1")
    String id;

    @CommandLine.Option(
            names = {"--name"},
            description = "Set the namespace name (omit for pre-allocated namespaces)",
            arity = "0..1")
    String name;

    @CommandLine.Option(
            names = {"--clear-name"},
            description = "Clear the namespace name (mark as pre-allocated)")
    boolean clearName;

    @CommandLine.Option(
            names = {"--path"},
            description = "Set the namespace path",
            arity = "0..1")
    String path;

    @CommandLine.Option(
            names = {"-l", "--label"},
            description = "Label key-value pair to add or update (e.g., '--label env=production')",
            arity = "0..*")
    Map<String, String> labels;

    NamespacesService namespacesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        namespacesService = initService(NamespacesService.class, host);

        if (clearName && name != null) {
            printer.printErrorMessage("Cannot specify both --name and --clear-name.");
            return EXIT_ERROR;
        }

        boolean hasUpdates = name != null || clearName || path != null || (labels != null && !labels.isEmpty());
        if (!hasUpdates) {
            printer.printErrorMessage("No updates specified. Use --name, --clear-name, --path, or --label.");
            return EXIT_ERROR;
        }

        try {
            WanakuResponse<Namespace> response = namespacesService.getById(id);
            Namespace namespace = response.data();

            if (namespace == null) {
                printer.printErrorMessage("Namespace not found: " + id);
                return EXIT_ERROR;
            }

            if (clearName) {
                namespace.setName(null);
            } else if (name != null) {
                String trimmed = name.trim();
                namespace.setName(trimmed.isEmpty() ? null : trimmed);
            }

            if (path != null) {
                namespace.setPath(path);
            }

            if (labels != null && !labels.isEmpty()) {
                Map<String, String> updatedLabels =
                        namespace.getLabels() == null ? new HashMap<>() : new HashMap<>(namespace.getLabels());
                updatedLabels.putAll(labels);
                namespace.setLabels(updatedLabels);
            }

            try (Response updateResponse = namespacesService.update(id, namespace)) {
                if (updateResponse.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                    printer.printErrorMessage("Namespace not found: " + id);
                    return EXIT_ERROR;
                }
                printer.printSuccessMessage("Namespace updated: " + id);
                return EXIT_OK;
            }
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
    }
}
