package ai.wanaku.cli.main.commands.datastores;

import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.IdSelector;
import ai.wanaku.cli.main.support.LabelHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.DataStoresService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;

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

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    IdSelector selector;

    @CommandLine.Option(
            names = {"-l", "--label"},
            description = "Label to add in key=value format (can be specified multiple times)",
            required = true,
            arity = "1..*")
    List<String> labels;

    DataStoresService dataStoresService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (dataStoresService == null) {
            dataStoresService = initAuthenticatedService(DataStoresService.class, host);
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
            WanakuResponse<DataStore> response = dataStoresService.getById(selector.id);
            DataStore dataStore = response.data();

            if (dataStore == null) {
                printer.printErrorMessage(String.format("Data store with ID '%s' not found", selector.id));
                return EXIT_ERROR;
            }

            return LabelHelper.addLabelsToEntity(
                    dataStore, labelsToAdd, printer, dataStoresService::update, "data store", selector.id);
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Data store", selector.id, printer);
        }
    }

    private Integer addLabelsByExpression(Map<String, String> labelsToAdd, WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<List<DataStore>> response = dataStoresService.list(selector.labelExpression);
            return LabelHelper.addLabelsByExpression(
                    response,
                    labelsToAdd,
                    printer,
                    dataStoresService::update,
                    DataStore::getId,
                    "data store(s)",
                    selector.labelExpression);
        } catch (WebApplicationException ex) {
            commonResponseErrorHandler(ex.getResponse());
            return EXIT_ERROR;
        }
    }
}
