package ai.wanaku.cli.main.commands.resources;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ResourcesService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

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
            resourcesService = initService(ResourcesService.class, host);
        }

        // Validate that either name or labelExpression is provided, but not both
        if (name != null && labelExpression != null) {
            printer.printErrorMessage("Cannot specify both --name and --label-expression. Use one or the other.");
            return EXIT_ERROR;
        }

        if (name == null && labelExpression == null) {
            printer.printErrorMessage("Must specify either --name or --label-expression.");
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

        // Handle adding labels by name
        return addLabelsByName(labelsToAdd, printer);
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
     * Adds labels to a single resource by name.
     *
     * @param labelsToAdd the labels to add
     * @param printer     the printer for displaying messages
     * @return exit code
     * @throws IOException if an I/O error occurs
     */
    private Integer addLabelsByName(Map<String, String> labelsToAdd, WanakuPrinter printer) throws IOException {
        try {
            // Get the existing resource
            WanakuResponse<ResourceReference> response = resourcesService.getByName(name);
            ResourceReference resource = response.data();

            if (resource == null) {
                printer.printErrorMessage(String.format("Resource '%s' not found", name));
                return EXIT_ERROR;
            }

            // Add new labels (this will override existing labels with same key)
            Map<String, String> existingLabels = resource.getLabels();
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

            resource.setLabels(existingLabels);

            // Update the resource
            Response updateResponse = resourcesService.update(resource);
            updateResponse.close();

            printer.printSuccessMessage(String.format(
                    "Labels updated for resource '%s' (%d added, %d updated)", name, addedCount, updatedCount));
            return EXIT_OK;

        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                printer.printErrorMessage(String.format("Resource '%s' not found", name));
            } else {
                commonResponseErrorHandler(response);
            }
            return EXIT_ERROR;
        }
    }

    /**
     * Adds labels to multiple resources matching a label expression.
     *
     * @param labelsToAdd the labels to add
     * @param printer     the printer for displaying messages
     * @return exit code
     * @throws IOException if an I/O error occurs
     */
    private Integer addLabelsByExpression(Map<String, String> labelsToAdd, WanakuPrinter printer) throws IOException {
        try {
            // Get all resources matching the label expression
            WanakuResponse<List<ResourceReference>> response = resourcesService.list(labelExpression);
            List<ResourceReference> matchingResources = response.data();

            if (matchingResources == null || matchingResources.isEmpty()) {
                printer.printWarningMessage("No resources found matching label expression: " + labelExpression);
                return EXIT_OK;
            }

            printer.printInfoMessage(String.format(
                    "Found %d resource(s) matching label expression '%s'", matchingResources.size(), labelExpression));

            int successCount = 0;
            int failureCount = 0;

            for (ResourceReference resource : matchingResources) {
                try {
                    // Add new labels
                    Map<String, String> existingLabels = resource.getLabels();
                    if (existingLabels == null) {
                        existingLabels = new HashMap<>();
                    }
                    existingLabels.putAll(labelsToAdd);
                    resource.setLabels(existingLabels);

                    // Update the resource
                    Response updateResponse = resourcesService.update(resource);
                    updateResponse.close();

                    printer.printSuccessMessage("  Updated: " + resource.getName());
                    successCount++;
                } catch (WebApplicationException ex) {
                    printer.printErrorMessage("  Failed to update: " + resource.getName());
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
