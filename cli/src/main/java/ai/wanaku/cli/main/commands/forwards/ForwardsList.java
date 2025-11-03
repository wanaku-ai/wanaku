package ai.wanaku.cli.main.commands.forwards;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ForwardsService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import org.jline.terminal.Terminal;

/**
 * Command to list port forwarding configurations in the Wanaku platform.
 * <p>
 * This command retrieves and displays all registered port forwards.
 * The output includes forward name and address for each entry.
 * </p>
 */
@Command(name = "list", description = "List forward targets")
public class ForwardsList extends BaseCommand {

    @Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {

        ForwardsService forwardsService = initService(ForwardsService.class, host);

        try {
            WanakuResponse<List<ForwardReference>> wanakuResponseRestResponse = forwardsService.listForwards(null);
            List<ForwardReference> data = wanakuResponseRestResponse.data();
            printer.printTable(data, "name", "address");

        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
