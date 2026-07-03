package ai.wanaku.cli.main.commands.datastores;

import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.LabelHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.DataStoresService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;

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
            dataStoresService = initAuthenticatedService(DataStoresService.class, host);
        }

        int validationResult = LabelHelper.validateLabelExpression(id, labelExpression, "--id", printer);
        if (validationResult != EXIT_OK) {
            return validationResult;
        }

        if (labelExpression != null) {
            return removeLabelsByExpression(printer);
        }

        return removeLabelsById(printer);
    }

    private Integer removeLabelsById(WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<DataStore> response = dataStoresService.getById(id);
            DataStore dataStore = response.data();

            if (dataStore == null) {
                printer.printErrorMessage(String.format("Data store with ID '%s' not found", id));
                return EXIT_ERROR;
            }

            return LabelHelper.removeLabelsFromEntity(
                    dataStore, labelKeys, printer, dataStoresService::update, "data store", id);
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Data store", id, printer);
        }
    }

    private Integer removeLabelsByExpression(WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<List<DataStore>> response = dataStoresService.list(labelExpression);
            return LabelHelper.removeLabelsByExpression(
                    response,
                    labelKeys,
                    printer,
                    dataStoresService::update,
                    DataStore::getId,
                    "data store(s)",
                    labelExpression);
        } catch (WebApplicationException ex) {
            commonResponseErrorHandler(ex.getResponse());
            return EXIT_ERROR;
        }
    }
}
