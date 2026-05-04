package ai.wanaku.cli.main.commands.toolset;

import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsetReposService;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "List registered toolset repositories")
public class ToolSetRepoList extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolSetRepoList.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ToolsetReposService service = initAuthenticatedService(ToolsetReposService.class, host);

        try {
            WanakuResponse<List<Map<String, String>>> response = service.list();
            List<Map<String, String>> repos = response.data();

            if (repos == null || repos.isEmpty()) {
                printer.printInfoMessage("No toolset repositories registered");
                return EXIT_OK;
            }

            for (Map<String, String> repo : repos) {
                printer.printInfoMessage(String.format(
                        "%-20s %-50s %s", repo.get("name"), repo.get("url"), repo.getOrDefault("description", "")));
            }
        } catch (Exception e) {
            printer.printErrorMessage(String.format("Failed to list toolset repositories: %s", e.getMessage()));
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
