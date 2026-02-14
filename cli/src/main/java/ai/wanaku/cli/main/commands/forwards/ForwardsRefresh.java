package ai.wanaku.cli.main.commands.forwards;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ForwardsService;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

@Command(name = "refresh", description = "Refresh forward to re-discover tools and resources")
public class ForwardsRefresh extends BaseCommand {
    @Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @Option(
            names = {"--name"},
            description = "The name of the forward to refresh",
            required = true,
            arity = "0..1")
    protected String name;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ForwardsService forwardsService = initService(ForwardsService.class, host);

        ForwardReference reference = new ForwardReference();
        reference.setName(name);

        try (Response ignored = forwardsService.refreshForward(reference)) {
            printer.printSuccessMessage("Successfully refreshed forward reference '" + reference.getName() + "'");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                String warningMessage = String.format(
                        "Forward not found (%s): %s%n",
                        name, response.getStatusInfo().getReasonPhrase());
                printer.printWarningMessage(warningMessage);
                return EXIT_ERROR;
            }
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
