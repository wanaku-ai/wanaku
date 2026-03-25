package ai.wanaku.cli.main.commands.datastores;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.DataStoresService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

/**
 * Get subcommand for data stores.
 */
@CommandLine.Command(name = "get", description = "Get a data store entry and decode its content")
public class DataStoresGet extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(DataStoresGet.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"--id"},
            description = "ID of the data store to retrieve",
            arity = "0..1")
    private String id;

    @CommandLine.Option(
            names = {"--name"},
            description = "Name of the data store to retrieve",
            arity = "0..1")
    private String name;

    @CommandLine.Option(
            names = {"--output-file"},
            description =
                    "Path to write the decoded content to (if not provided, writes to stdout or current directory)",
            arity = "0..1")
    private String outputFile;

    DataStoresService dataStoresService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        // Validate that either id or name is provided
        if ((id == null || id.trim().isEmpty()) && (name == null || name.trim().isEmpty())) {
            printer.printErrorMessage("Either --id or --name must be provided%n");
            return EXIT_ERROR;
        }

        // Prefer id if both are provided
        if (id != null && !id.trim().isEmpty() && name != null && !name.trim().isEmpty()) {
            printer.printWarningMessage("Both --id and --name provided, using --id%n");
        }

        dataStoresService = initService(DataStoresService.class, host);

        try {
            DataStore dataStore = null;
            String fileName = null;

            // Fetch by ID or name
            if (id != null && !id.trim().isEmpty()) {
                WanakuResponse<DataStore> response = dataStoresService.getById(id);
                dataStore = response.data();
                fileName = dataStore.getName();
            } else {
                WanakuResponse<List<DataStore>> response = dataStoresService.getByName(name);
                List<DataStore> dataStores = response.data();

                if (dataStores == null || dataStores.isEmpty()) {
                    printer.printErrorMessage(String.format("No data store found with name: %s%n", name));
                    return EXIT_ERROR;
                }

                if (dataStores.size() > 1) {
                    printer.printWarningMessage(
                            String.format("Multiple data stores found with name '%s', using the first one%n", name));
                }

                dataStore = dataStores.get(0);
                fileName = dataStore.getName();
            }

            if (dataStore == null) {
                printer.printErrorMessage("Data store not found%n");
                return EXIT_ERROR;
            }

            // Decode Base64 data
            byte[] decodedData;
            try {
                decodedData = Base64.getDecoder().decode(dataStore.getData());
            } catch (IllegalArgumentException e) {
                printer.printErrorMessage(String.format("Failed to decode data: %s%n", e.getMessage()));
                return EXIT_ERROR;
            }

            // Determine output path
            Path outputPath;
            if (outputFile != null && !outputFile.trim().isEmpty()) {
                outputPath = Path.of(outputFile);
            } else {
                // Use the data store name as the file name in the current directory
                outputPath = Path.of(fileName);
            }

            // Write to file
            try {
                Files.write(outputPath, decodedData);
                printer.printSuccessMessage(String.format(
                        "Successfully retrieved data store '%s' (ID: %s) and saved to: %s (%d bytes)%n",
                        dataStore.getName(), dataStore.getId(), outputPath.toAbsolutePath(), decodedData.length));
            } catch (IOException e) {
                printer.printErrorMessage(
                        String.format("Failed to write to file '%s': %s%n", outputPath, e.getMessage()));
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
