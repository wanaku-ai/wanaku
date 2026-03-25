package ai.wanaku.cli.main.commands.prompts;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.PromptsService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

@CommandLine.Command(name = "list", description = "List prompts")
public class PromptsList extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(PromptsList.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    PromptsService promptsService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        promptsService = initService(PromptsService.class, host);
        try {
            WanakuResponse<List<PromptReference>> response = promptsService.list();
            List<PromptReference> list = response.data();
            list.stream().filter(p -> p.getNamespace() == null).forEach(p -> p.setNamespace("default"));
            printer.printTable(list, "name", "namespace", "description");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
