package ai.wanaku.cli.main.commands.datastores;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.DataStoresService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Remove subcommand for data stores.
 */
@CommandLine.Command(name = "remove", description = "Remove data store entries")
public class DataStoresRemove extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(DataStoresRemove.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"--id"},
            description = "ID of the data store to remove",
            arity = "0..1")
    private String id;

    @CommandLine.Option(
            names = {"--name"},
            description = "Name of the data store(s) to remove",
            arity = "0..1")
    private String name;

    DataStoresService dataStoresService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        // Validate that either id or name is provided, but not both
        if ((id == null || id.trim().isEmpty()) && (name == null || name.trim().isEmpty())) {
            printer.printErrorMessage("Either --id or --name must be provided%n");
            CommandLine.usage(this, System.out);
            return EXIT_ERROR;
        }

        if (id != null && !id.trim().isEmpty() && name != null && !name.trim().isEmpty()) {
            printer.printWarningMessage("Both --id and --name provided. Using --id only.%n");
        }

        dataStoresService = initService(DataStoresService.class, host);

        try {
            Response response;
            String identifier;

            // Prefer ID over name if both provided
            if (id != null && !id.trim().isEmpty()) {
                response = dataStoresService.remove(id.trim());
                identifier = "ID '" + id.trim() + "'";
            } else {
                response = dataStoresService.removeByName(name.trim());
                identifier = "name '" + name.trim() + "'";
            }

            if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                printer.printSuccessMessage(String.format("Successfully removed data store(s) with %s%n", identifier));
                LOG.debugf("Removed data store(s) with %s", identifier);
            } else if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                printer.printWarningMessage(String.format("No data store found with %s%n", identifier));
                return EXIT_ERROR;
            } else {
                commonResponseErrorHandler(response);
                return EXIT_ERROR;
            }

        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }

        return EXIT_OK;
    }
}
