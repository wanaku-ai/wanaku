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
 * CLI command for adding labels to existing data stores.
 * <p>
 * This command allows you to add one or more labels to a data store without modifying
 * its other properties. If a label key already exists, its value will be updated.
 * </p>
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * # Add a single label by ID
 * wanaku datastores label add --id abc123 --label env=production
 *
 * # Add multiple labels
 * wanaku datastores label add --id abc123 -l env=production -l tier=backend -l version=2.0
 *
 * # Add labels using label expression to select data stores
 * wanaku datastores label add --label-expression 'category=config' --label migrated=true
 * </pre>
 *
 * @see DataStoresLabelRemove
 * @see DataStoresLabel
 */
@CommandLine.Command(name = "add", description = "Add labels to data stores")
public class DataStoresLabelAdd extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"--id"},
            description = "ID of the data store to add labels to. Cannot be used with --label-expression.")
    String id;

    @CommandLine.Option(
            names = {"-l", "--label"},
            description = "Label to add in key=value format (can be specified multiple times)",
            required = true,
            arity = "1..*")
    List<String> labels;

    @CommandLine.Option(
            names = {"-e", "--label-expression"},
            description = "Add labels to all data stores matching this label expression. Cannot be used with --id.")
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

        // Parse labels
        Map<String, String> labelsToAdd = parseLabels(printer);
        if (labelsToAdd == null) {
            return EXIT_ERROR;
        }

        // Handle adding labels by label expression
        if (labelExpression != null) {
            return addLabelsByExpression(labelsToAdd, printer);
        }

        // Handle adding labels by ID
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
     * Adds labels to a single data store by ID.
     *
     * @param labelsToAdd the labels to add
     * @param printer     the printer for displaying messages
     * @return exit code
     * @throws IOException if an I/O error occurs
     */
    private Integer addLabelsById(Map<String, String> labelsToAdd, WanakuPrinter printer) throws IOException {
        try {
            // Get the existing data store
            WanakuResponse<DataStore> response = dataStoresService.getById(id);
            DataStore dataStore = response.data();

            if (dataStore == null) {
                printer.printErrorMessage(String.format("Data store with ID '%s' not found", id));
                return EXIT_ERROR;
            }

            // Add new labels (this will override existing labels with same key)
            Map<String, String> existingLabels = dataStore.getLabels();
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

            dataStore.setLabels(existingLabels);

            // Update the data store
            Response updateResponse = dataStoresService.update(dataStore);
            updateResponse.close();

            printer.printSuccessMessage(String.format(
                    "Labels updated for data store '%s' (%d added, %d updated)", id, addedCount, updatedCount));
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
     * Adds labels to multiple data stores matching a label expression.
     *
     * @param labelsToAdd the labels to add
     * @param printer     the printer for displaying messages
     * @return exit code
     * @throws IOException if an I/O error occurs
     */
    private Integer addLabelsByExpression(Map<String, String> labelsToAdd, WanakuPrinter printer) throws IOException {
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
                    // Add new labels
                    Map<String, String> existingLabels = dataStore.getLabels();
                    if (existingLabels == null) {
                        existingLabels = new HashMap<>();
                    }
                    existingLabels.putAll(labelsToAdd);
                    dataStore.setLabels(existingLabels);

                    // Update the data store
                    Response updateResponse = dataStoresService.update(dataStore);
                    updateResponse.close();

                    printer.printSuccessMessage("  Updated: " + dataStore.getId());
                    successCount++;
                } catch (WebApplicationException ex) {
                    printer.printErrorMessage("  Failed to update: " + dataStore.getId());
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
