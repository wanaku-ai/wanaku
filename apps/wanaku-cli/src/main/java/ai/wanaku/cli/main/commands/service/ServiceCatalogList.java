package ai.wanaku.cli.main.commands.service;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ServiceCatalogService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

@CommandLine.Command(name = "list", description = "List all service catalogs")
public class ServiceCatalogList extends BaseCommand {

    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_DESCRIPTION = "description";

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"--search"},
            description = "Optional search term to filter catalogs",
            arity = "0..1")
    private String search;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ServiceCatalogService service = initService(ServiceCatalogService.class, host);

        try {
            WanakuResponse<List<Map<String, Object>>> response = service.list(search);
            List<Map<String, Object>> catalogs = response.data();

            if (catalogs == null || catalogs.isEmpty()) {
                printer.printInfoMessage("No service catalogs found");
                return EXIT_OK;
            }

            printer.printInfoMessage("Available service catalogs:");
            printer.printTable(catalogs, COLUMN_NAME, COLUMN_DESCRIPTION);
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }

        return EXIT_OK;
    }
}
