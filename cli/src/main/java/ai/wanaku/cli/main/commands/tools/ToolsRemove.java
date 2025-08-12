package ai.wanaku.cli.main.commands.tools;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(name = "remove", description = "Remove tools")
public class ToolsRemove extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolsRemove.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"-n", "--name"},
            description = "Name of the tool to remove",
            required = true)
    private String name;

    ToolsService toolsService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        toolsService = initService(ToolsService.class, host);
        try (Response ignored = toolsService.remove(name)) {
            printer.printSuccessMessage("Successfully removed tool reference '" + name + "'");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                String warningMessage = String.format(
                        "Tool not found (%s): %s%n",
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
