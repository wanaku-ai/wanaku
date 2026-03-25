package ai.wanaku.cli.main.commands.namespaces;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
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

    @CommandLine.Option(
            names = {"-i", "--id"},
            description = "ID of the namespace to remove labels from. Cannot be used with --label-expression.")
    String id;

    @CommandLine.Option(
            names = {"-l", "--label"},
            description = "Label key to remove (can be specified multiple times)",
            required = true,
            arity = "1..*")
    List<String> labels;

    @CommandLine.Option(
            names = {"-e", "--label-expression"},
            description = "Remove labels from all namespaces matching this label expression. Cannot be used with --id.")
    String labelExpression;

    NamespacesService namespacesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (namespacesService == null) {
            namespacesService = initService(NamespacesService.class, host);
        }

        // Validate that either id or labelExpression is provided, but not both
        if (id != null && labelExpression != null) {
            printer.printErrorMessage("Cannot specify both --id and --label-expression. Use one or the other.");
            return EXIT_ERROR;
        }

        if (id == null && labelExpression == null) {
            printer.printErrorMessage("Must specify either --id or --label-expression.");
            return EXIT_ERROR;
        }

        // Handle removing labels by label expression
        if (labelExpression != null) {
            return removeLabelsByExpression(printer);
        }

        // Handle removing labels by id
        return removeLabelsById(printer);
    }

    /**
     * Removes labels from a single namespace by id.
     *
     * @param printer the printer for displaying messages
     * @return exit code
     * @throws IOException if an I/O error occurs
     */
    private Integer removeLabelsById(WanakuPrinter printer) throws IOException {
        try {
            // Get the existing namespace
            WanakuResponse<Namespace> response = namespacesService.getById(id);
            Namespace namespace = response.data();

            if (namespace == null) {
                printer.printErrorMessage(String.format("Namespace with ID '%s' not found", id));
                return EXIT_ERROR;
            }

            // Remove labels
            Map<String, String> existingLabels = namespace.getLabels();
            if (existingLabels == null) {
                existingLabels = new HashMap<>();
            }

            int removedCount = 0;
            int notFoundCount = 0;

            for (String labelKey : labels) {
                if (existingLabels.containsKey(labelKey)) {
                    existingLabels.remove(labelKey);
                    printer.printInfoMessage(String.format("Removing label '%s'", labelKey));
                    removedCount++;
                } else {
                    notFoundCount++;
                }
            }

            namespace.setLabels(existingLabels);

            // Update the namespace
            Response updateResponse = namespacesService.update(id, namespace);
            updateResponse.close();

            printer.printSuccessMessage(String.format(
                    "Labels updated for namespace with ID '%s' (%d removed, %d not found)",
                    id, removedCount, notFoundCount));
            return EXIT_OK;

        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                printer.printErrorMessage(String.format("Namespace with ID '%s' not found", id));
            } else {
                commonResponseErrorHandler(response);
            }
            return EXIT_ERROR;
        }
    }

    /**
     * Removes labels from multiple namespaces matching a label expression.
     *
     * @param printer the printer for displaying messages
     * @return exit code
     * @throws IOException if an I/O error occurs
     */
    private Integer removeLabelsByExpression(WanakuPrinter printer) throws IOException {
        try {
            // Get all namespaces matching the label expression
            WanakuResponse<List<Namespace>> response = namespacesService.list(labelExpression);
            List<Namespace> matchingNamespaces = response.data();

            if (matchingNamespaces == null || matchingNamespaces.isEmpty()) {
                printer.printWarningMessage("No namespaces found matching label expression: " + labelExpression);
                return EXIT_OK;
            }

            printer.printInfoMessage(String.format(
                    "Found %d namespace(s) matching label expression '%s'",
                    matchingNamespaces.size(), labelExpression));

            int successCount = 0;
            int failureCount = 0;

            for (Namespace namespace : matchingNamespaces) {
                try {
                    // Remove labels
                    Map<String, String> existingLabels = namespace.getLabels();
                    if (existingLabels == null) {
                        existingLabels = new HashMap<>();
                    }

                    for (String labelKey : labels) {
                        existingLabels.remove(labelKey);
                    }

                    namespace.setLabels(existingLabels);

                    // Update the namespace
                    Response updateResponse = namespacesService.update(namespace.getId(), namespace);
                    updateResponse.close();

                    printer.printSuccessMessage("  Updated: " + namespace.getId());
                    successCount++;
                } catch (WebApplicationException ex) {
                    printer.printErrorMessage("  Failed to update: " + namespace.getId());
                    failureCount++;
                }
            }

            printer.printInfoMessage(
                    String.format("Label update complete: %d succeeded, %d failed", successCount, failureCount));

            return failureCount > 0 ? EXIT_ERROR : EXIT_OK;
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
    }
}
