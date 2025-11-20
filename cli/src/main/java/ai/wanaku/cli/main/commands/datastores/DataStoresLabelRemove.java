package ai.wanaku.cli.main.commands.datastores;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.api.types.DataStore;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.DataStoresService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * CLI command for removing labels from existing data stores.
 * <p>
 * This command allows you to remove one or more labels from a data store without modifying
 * its other properties. If a label key doesn't exist, it will be silently ignored.
 * </p>
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * # Remove a single label by ID
 * wanaku datastores label remove --id abc123 --label env
 *
 * # Remove multiple labels
 * wanaku datastores label remove --id abc123 -l env -l tier -l version
 *
 * # Remove labels from data stores matching a label expression
 * wanaku datastores label remove --label-expression 'status=deprecated' --label temporary
 * </pre>
 *
 * @see DataStoresLabelAdd
 * @see DataStoresLabel
 */
@CommandLine.Command(name = "remove", description = "Remove labels from data stores")
public class DataStoresLabelRemove extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"--id"},
            description = "ID of the data store to remove labels from. Cannot be used with --label-expression.")
    String id;

    @CommandLine.Option(
            names = {"-l", "--label"},
            description = "Label key to remove (can be specified multiple times)",
            required = true,
            arity = "1..*")
    List<String> labelKeys;

    @CommandLine.Option(
            names = {"-e", "--label-expression"},
            description =
                    "Remove labels from all data stores matching this label expression. Cannot be used with --id.")
    String labelExpression;

    DataStoresService dataStoresService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (dataStoresService == null) {
            dataStoresService = initService(DataStoresService.class, host);
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

        // Handle removing labels by ID
        return removeLabelsById(printer);
    }

    /**
     * Removes labels from a single data store by ID.
     *
     * @param printer the printer for displaying messages
     * @return exit code
     * @throws IOException if an I/O error occurs
     */
    private Integer removeLabelsById(WanakuPrinter printer) throws IOException {
        try {
            // Get the existing data store
            WanakuResponse<DataStore> response = dataStoresService.getById(id);
            DataStore dataStore = response.data();

            if (dataStore == null) {
                printer.printErrorMessage(String.format("Data store with ID '%s' not found", id));
                return EXIT_ERROR;
            }

            // Remove labels
            Map<String, String> existingLabels = dataStore.getLabels();
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
                dataStore.setLabels(existingLabels);

                // Update the data store
                Response updateResponse = dataStoresService.update(dataStore);
                updateResponse.close();

                printer.printSuccessMessage(String.format(
                        "Labels updated for data store '%s' (%d removed, %d not found)",
                        id, removedCount, notFoundCount));
            } else {
                printer.printWarningMessage("No labels were removed");
            }

            return EXIT_OK;

        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                printer.printErrorMessage(String.format("Data store with ID '%s' not found", id));
            } else {
                commonResponseErrorHandler(response);
            }
            return EXIT_ERROR;
        }
    }

    /**
     * Removes labels from multiple data stores matching a label expression.
     *
     * @param printer the printer for displaying messages
     * @return exit code
     * @throws IOException if an I/O error occurs
     */
    private Integer removeLabelsByExpression(WanakuPrinter printer) throws IOException {
        try {
            // Get all data stores matching the label expression
            WanakuResponse<List<DataStore>> response = dataStoresService.list(labelExpression);
            List<DataStore> matchingDataStores = response.data();

            if (matchingDataStores == null || matchingDataStores.isEmpty()) {
                printer.printWarningMessage("No data stores found matching label expression: " + labelExpression);
                return EXIT_OK;
            }

            printer.printInfoMessage(String.format(
                    "Found %d data store(s) matching label expression '%s'",
                    matchingDataStores.size(), labelExpression));

            int successCount = 0;
            int failureCount = 0;

            for (DataStore dataStore : matchingDataStores) {
                try {
                    // Remove labels
                    Map<String, String> existingLabels = dataStore.getLabels();
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
                        dataStore.setLabels(existingLabels);

                        // Update the data store
                        Response updateResponse = dataStoresService.update(dataStore);
                        updateResponse.close();

                        printer.printSuccessMessage("  Updated: " + dataStore.getId());
                        successCount++;
                    } else {
                        printer.printInfoMessage("  No changes: " + dataStore.getId());
                    }
                } catch (WebApplicationException ex) {
                    printer.printErrorMessage("  Failed to update: " + dataStore.getId());
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
