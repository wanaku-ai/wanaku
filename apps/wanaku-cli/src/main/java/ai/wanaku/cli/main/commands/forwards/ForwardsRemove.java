package ai.wanaku.cli.main.commands.forwards;

import jakarta.ws.rs.WebApplicationException;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ForwardsService;

import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

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

        try {
            forwardsService.removeForward(name);
            printer.printSuccessMessage("Successfully removed forward reference '" + name + "'");
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Forward", name, printer);
        }
        return EXIT_OK;
    }
}
