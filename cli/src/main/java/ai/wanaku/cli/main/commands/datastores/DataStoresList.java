package ai.wanaku.cli.main.commands.datastores;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.api.types.DataStore;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.DataStoresService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * List subcommand for data stores.
 */
@CommandLine.Command(name = "list", description = "List all data store entries")
public class DataStoresList extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    DataStoresService dataStoresService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        dataStoresService = initService(DataStoresService.class, host);

        try {
            WanakuResponse<List<DataStore>> response = dataStoresService.list();
            List<DataStore> dataStores = response.data();

            if (dataStores == null || dataStores.isEmpty()) {
                printer.printInfoMessage("No data stores found.%n");
                return EXIT_OK;
            }

            // Create a list with truncated data for display
            List<DataStoreDisplay> displayList = dataStores.stream()
                    .map(ds -> new DataStoreDisplay(ds.getId(), ds.getName(), truncateData(ds.getData())))
                    .collect(Collectors.toList());

            printer.printTable(displayList, "id", "name", "dataTruncated");

        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }

        return EXIT_OK;
    }

    /**
     * Truncate base64 data for display purposes.
     */
    private String truncateData(String data) {
        if (data == null) {
            return "";
        }
        if (data.length() <= 50) {
            return data;
        }
        return data.substring(0, 47) + "...";
    }

    /**
     * Display class for table printing.
     */
    public static class DataStoreDisplay {
        private String id;
        private String name;
        private String dataTruncated;

        public DataStoreDisplay(String id, String name, String dataTruncated) {
            this.id = id;
            this.name = name;
            this.dataTruncated = dataTruncated;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDataTruncated() {
            return dataTruncated;
        }
    }
}
