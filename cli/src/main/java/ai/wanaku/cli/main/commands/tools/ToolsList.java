package ai.wanaku.cli.main.commands.tools;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

import java.util.List;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

@CommandLine.Command(name = "list", description = "List tools")
public class ToolsList extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolsList.class);

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    ToolsService toolsService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        toolsService = initService(ToolsService.class, host);
        try {
            WanakuResponse<List<ToolReference>> response = toolsService.list();
            List<ToolReference> list = response.data();
            printer.printTable(list, "name","type","uri");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
