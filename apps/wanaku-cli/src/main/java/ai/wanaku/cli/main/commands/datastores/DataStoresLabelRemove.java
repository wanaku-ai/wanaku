package ai.wanaku.cli.main.commands.datastores;

import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.util.List;
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

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    IdSelector selector;

    @CommandLine.Option(
            names = {"-l", "--label"},
            description = "Label key to remove (can be specified multiple times)",
            required = true,
            arity = "1..*")
    List<String> labelKeys;

    DataStoresService dataStoresService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (dataStoresService == null) {
            dataStoresService = initAuthenticatedService(DataStoresService.class, host);
        }

        if (selector.labelExpression != null) {
            return removeLabelsByExpression(printer);
        }

        return removeLabelsById(printer);
    }

    private Integer removeLabelsById(WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<DataStore> response = dataStoresService.getById(selector.id);
            DataStore dataStore = response.data();

            if (dataStore == null) {
                printer.printErrorMessage(String.format("Data store with ID '%s' not found", selector.id));
                return EXIT_ERROR;
            }

            return LabelHelper.removeLabelsFromEntity(
                    dataStore, labelKeys, printer, dataStoresService::update, "data store", selector.id);
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Data store", selector.id, printer);
        }
    }

    private Integer removeLabelsByExpression(WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<List<DataStore>> response = dataStoresService.list(selector.labelExpression);
            return LabelHelper.removeLabelsByExpression(
                    response,
                    labelKeys,
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
