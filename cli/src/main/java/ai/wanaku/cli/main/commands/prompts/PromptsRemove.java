package ai.wanaku.cli.main.commands.prompts;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.PromptsService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

@CommandLine.Command(name = "remove", description = "Remove prompts")
public class PromptsRemove extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(PromptsRemove.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"-n", "--name"},
            description = "Name of the prompt to remove",
            required = true)
    private String name;

    PromptsService promptsService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        promptsService = initService(PromptsService.class, host);
        try {
            promptsService.remove(name);
            printer.printSuccessMessage("Successfully removed prompt reference '" + name + "'");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                String warningMessage = String.format(
                        "Prompt not found (%s): %s%n",
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
