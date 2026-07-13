package ai.wanaku.cli.main.commands.namespaces;

import jakarta.ws.rs.WebApplicationException;

import java.util.HashMap;
import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.NamespacesService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;

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

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    NameUpdate nameUpdate;

    static class NameUpdate {
        @CommandLine.Option(
                names = {"--name"},
                description = "Set the namespace name (omit for pre-allocated namespaces)",
                arity = "0..1")
        String name;

        @CommandLine.Option(
                names = {"--clear-name"},
                description = "Clear the namespace name (mark as pre-allocated)")
        boolean clearName;
    }

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
        namespacesService = initAuthenticatedServiceIfNeeded(namespacesService, NamespacesService.class, host);

        boolean hasUpdates = nameUpdate != null || path != null || (labels != null && !labels.isEmpty());
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

            if (nameUpdate != null) {
                if (nameUpdate.clearName) {
                    namespace.setName(null);
                } else if (nameUpdate.name != null) {
                    String trimmed = nameUpdate.name.trim();
                    namespace.setName(trimmed.isEmpty() ? null : trimmed);
                }
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

            namespacesService.update(id, namespace);
            printer.printSuccessMessage("Namespace updated: " + id);
            return EXIT_OK;
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Namespace", id, printer);
        }
    }
}
