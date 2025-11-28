package ai.wanaku.cli.main.commands.datastores;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.DataStoresService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Add subcommand for data stores.
 */
@CommandLine.Command(name = "add", description = "Add a data store entry")
public class DataStoresAdd extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(DataStoresAdd.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"--read-from-file"},
            description = "Path to the file to store (content will be base64 encoded)",
            required = true)
    private String filePath;

    @CommandLine.Option(
            names = {"--name"},
            description = "Name for the data store entry (defaults to filename if not provided)",
            arity = "0..1")
    private String name;

    DataStoresService dataStoresService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        // Read file content
        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(Path.of(filePath));
        } catch (IOException e) {
            printer.printErrorMessage(String.format("Failed to read file '%s': %s%n", filePath, e.getMessage()));
            return EXIT_ERROR;
        }

        // Encode to Base64
        String base64Data = Base64.getEncoder().encodeToString(fileBytes);

        // Determine name: use provided name or extract from file path
        String dataStoreName;
        if (name != null && !name.trim().isEmpty()) {
            dataStoreName = name.trim();
        } else {
            dataStoreName = Path.of(filePath).getFileName().toString();
        }

        // Create DataStore object
        DataStore dataStore = new DataStore();
        dataStore.setName(dataStoreName);
        dataStore.setData(base64Data);

        // Call API
        dataStoresService = initService(DataStoresService.class, host);

        try {
            WanakuResponse<DataStore> response = dataStoresService.add(dataStore);
            DataStore created = response.data();

            printer.printSuccessMessage(String.format(
                    "Successfully added data store '%s' with ID: %s%n", created.getName(), created.getId()));

            LOG.debugf("Created data store: %s", created);
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }

        return EXIT_OK;
    }
}
