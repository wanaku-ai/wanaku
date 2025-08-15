package ai.wanaku.cli.main.commands.forwards;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ForwardsService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jline.terminal.Terminal;

@Command(name = "remove", description = "Remove forward targets")
public class ForwardsRemove extends BaseCommand {
    @Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @Option(
            names = {"--name"},
            description = "The name of the service to forward",
            required = true,
            arity = "0..1")
    protected String name;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ForwardsService forwardsService = initService(ForwardsService.class, host);
        ForwardReference reference = new ForwardReference();
        reference.setName(name);

        try (Response ignored = forwardsService.removeForward(reference)) {
            printer.printSuccessMessage("Successfully removed forward reference '" + reference.getName() + "'");
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
