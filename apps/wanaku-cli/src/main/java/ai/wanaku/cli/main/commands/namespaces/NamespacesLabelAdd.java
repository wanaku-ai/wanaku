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

    @CommandLine.Option(
            names = {"-i", "--id"},
            description = "ID of the namespace to add labels to. Cannot be used with --label-expression.")
    String id;

    @CommandLine.Option(
            names = {"-l", "--label"},
            description = "Label to add in key=value format (can be specified multiple times)",
            required = true,
            arity = "1..*")
    List<String> labels;

    @CommandLine.Option(
            names = {"-e", "--label-expression"},
            description = "Add labels to all namespaces matching this label expression. Cannot be used with --id.")
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

        // Parse labels
        Map<String, String> labelsToAdd = parseLabels(printer);
        if (labelsToAdd == null) {
            return EXIT_ERROR;
        }

        // Handle adding labels by label expression
        if (labelExpression != null) {
            return addLabelsByExpression(labelsToAdd, printer);
        }

        // Handle adding labels by id
        return addLabelsById(labelsToAdd, printer);
    }

    /**
     * Parses label strings into a map.
     *
     * @param printer the printer for error messages
     * @return map of labels or null if parsing failed
     */
    private Map<String, String> parseLabels(WanakuPrinter printer) {
        Map<String, String> labelMap = new HashMap<>();
        for (String label : labels) {
            String[] parts = label.split("=", 2);
            if (parts.length == 2) {
                labelMap.put(parts[0].trim(), parts[1].trim());
            } else {
                printer.printErrorMessage(
                        String.format("Invalid label format: '%s'. Expected format: 'key=value'", label));
                return null;
            }
        }
        return labelMap;
    }

    /**
     * Adds labels to a single namespace by id.
     *
     * @param labelsToAdd the labels to add
     * @param printer     the printer for displaying messages
     * @return exit code
     * @throws IOException if an I/O error occurs
     */
    private Integer addLabelsById(Map<String, String> labelsToAdd, WanakuPrinter printer) throws IOException {
        try {
            // Get the existing namespace
            WanakuResponse<Namespace> response = namespacesService.getById(id);
            Namespace namespace = response.data();

            if (namespace == null) {
                printer.printErrorMessage(String.format("Namespace with ID '%s' not found", id));
                return EXIT_ERROR;
            }

            // Add new labels (this will override existing labels with same key)
            Map<String, String> existingLabels = namespace.getLabels();
            if (existingLabels == null) {
                existingLabels = new HashMap<>();
            }

            int addedCount = 0;
            int updatedCount = 0;

            for (Map.Entry<String, String> entry : labelsToAdd.entrySet()) {
                if (existingLabels.containsKey(entry.getKey())) {
                    String oldValue = existingLabels.get(entry.getKey());
                    if (!oldValue.equals(entry.getValue())) {
                        printer.printInfoMessage(String.format(
                                "Updating label '%s': '%s' -> '%s'", entry.getKey(), oldValue, entry.getValue()));
                        updatedCount++;
                    }
                } else {
                    printer.printInfoMessage(
                            String.format("Adding label '%s' = '%s'", entry.getKey(), entry.getValue()));
                    addedCount++;
                }
                existingLabels.put(entry.getKey(), entry.getValue());
            }

            namespace.setLabels(existingLabels);

            // Update the namespace
            Response updateResponse = namespacesService.update(id, namespace);
            updateResponse.close();

            printer.printSuccessMessage(String.format(
                    "Labels updated for namespace with ID '%s' (%d added, %d updated)", id, addedCount, updatedCount));
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
     * Adds labels to multiple namespaces matching a label expression.
     *
     * @param labelsToAdd the labels to add
     * @param printer     the printer for displaying messages
     * @return exit code
     * @throws IOException if an I/O error occurs
     */
    private Integer addLabelsByExpression(Map<String, String> labelsToAdd, WanakuPrinter printer) throws IOException {
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
                    // Add new labels
                    Map<String, String> existingLabels = namespace.getLabels();
                    if (existingLabels == null) {
                        existingLabels = new HashMap<>();
                    }
                    existingLabels.putAll(labelsToAdd);
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
