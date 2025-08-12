package ai.wanaku.cli.main.commands.resources;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ResourcesService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jline.terminal.Terminal;

@Command(name = "remove", description = "Remove exposed resources")
public class ResourcesRemove extends BaseCommand {

    @Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @Option(
            names = {"--name"},
            description = "A human-readable name for the resource",
            required = true,
            arity = "0..1")
    private String name;

    ResourcesService resourcesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        resourcesService = initService(ResourcesService.class, host);

        try (Response response = resourcesService.remove(name)) {
            printer.printSuccessMessage("Successfully removed resource reference '" + name + "'");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                String warningMessage = String.format(
                        "Resource not found (%s): %s%n",
                        name, response.getStatusInfo().getReasonPhrase());
                printer.printWarningMessage(warningMessage);
            } else {
                commonResponseErrorHandler(response);
            }
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
