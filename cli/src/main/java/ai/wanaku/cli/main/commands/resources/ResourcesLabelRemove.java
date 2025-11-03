package ai.wanaku.cli.main.commands.resources;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.WanakuResponse;
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

    @CommandLine.Option(
            names = {"-n", "--name"},
            description = "Name of the resource to remove labels from. Cannot be used with --label-expression.")
    String name;

    @CommandLine.Option(
            names = {"-l", "--label"},
            description = "Label key to remove (can be specified multiple times)",
            required = true,
            arity = "1..*")
    List<String> labelKeys;

    @CommandLine.Option(
            names = {"-e", "--label-expression"},
            description =
                    "Remove labels from all resources matching this label expression. Cannot be used with --name.")
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

        // Handle removing labels by label expression
        if (labelExpression != null) {
            return removeLabelsByExpression(printer);
        }

        // Handle removing labels by name
        return removeLabelsByName(printer);
    }

    /**
     * Removes labels from a single resource by name.
     *
     * @param printer the printer for displaying messages
     * @return exit code
     * @throws IOException if an I/O error occurs
     */
    private Integer removeLabelsByName(WanakuPrinter printer) throws IOException {
        try {
            // Get the existing resource
            WanakuResponse<ResourceReference> response = resourcesService.getByName(name);
            ResourceReference resource = response.data();

            if (resource == null) {
                printer.printErrorMessage(String.format("Resource '%s' not found", name));
                return EXIT_ERROR;
            }

            // Remove labels
            Map<String, String> existingLabels = resource.getLabels();
            if (existingLabels == null) {
                existingLabels = new HashMap<>();
            }

            int removedCount = 0;
            int notFoundCount = 0;

            for (String labelKey : labelKeys) {
                if (existingLabels.containsKey(labelKey)) {
                    String removedValue = existingLabels.remove(labelKey);
                    printer.printInfoMessage(String.format("Removed label '%s' (was: '%s')", labelKey, removedValue));
                    removedCount++;
                } else {
                    printer.printWarningMessage(String.format("Label '%s' not found, skipping", labelKey));
                    notFoundCount++;
                }
            }

            if (removedCount > 0) {
                resource.setLabels(existingLabels);

                // Update the resource
                Response updateResponse = resourcesService.update(resource);
                updateResponse.close();

                printer.printSuccessMessage(String.format(
                        "Labels updated for resource '%s' (%d removed, %d not found)",
                        name, removedCount, notFoundCount));
            } else {
                printer.printWarningMessage("No labels were removed");
            }

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
     * Removes labels from multiple resources matching a label expression.
     *
     * @param printer the printer for displaying messages
     * @return exit code
     * @throws IOException if an I/O error occurs
     */
    private Integer removeLabelsByExpression(WanakuPrinter printer) throws IOException {
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
                    // Remove labels
                    Map<String, String> existingLabels = resource.getLabels();
                    if (existingLabels == null) {
                        existingLabels = new HashMap<>();
                    }

                    boolean modified = false;
                    for (String labelKey : labelKeys) {
                        if (existingLabels.remove(labelKey) != null) {
                            modified = true;
                        }
                    }

                    if (modified) {
                        resource.setLabels(existingLabels);

                        // Update the resource
                        Response updateResponse = resourcesService.update(resource);
                        updateResponse.close();

                        printer.printSuccessMessage("  Updated: " + resource.getName());
                        successCount++;
                    } else {
                        printer.printInfoMessage("  No changes: " + resource.getName());
                    }
                } catch (WebApplicationException ex) {
                    printer.printErrorMessage("  Failed to update: " + resource.getName());
                    failureCount++;
                }
            }

            printer.printInfoMessage(
                    String.format("Label removal complete: %d succeeded, %d failed", successCount, failureCount));

            return failureCount > 0 ? EXIT_ERROR : EXIT_OK;
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
    }
}
