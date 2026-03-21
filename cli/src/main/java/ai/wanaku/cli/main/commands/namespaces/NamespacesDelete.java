package ai.wanaku.cli.main.commands.namespaces;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.NamespacesService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

/**
 * CLI command for deleting namespaces by id.
 */
@CommandLine.Command(name = "delete", description = "Delete a namespace by id")
public class NamespacesDelete extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Parameters(description = "The ID of the namespace to delete", arity = "1")
    String id;

    NamespacesService namespacesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        namespacesService = initServiceIfNeeded(namespacesService, NamespacesService.class, host);

        try (Response response = namespacesService.delete(id)) {
            printer.printSuccessMessage("Namespace deleted: " + id);
            return EXIT_OK;
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                printer.printWarningMessage("Namespace not found: " + id);
                return EXIT_ERROR;
            }
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
    }
}
