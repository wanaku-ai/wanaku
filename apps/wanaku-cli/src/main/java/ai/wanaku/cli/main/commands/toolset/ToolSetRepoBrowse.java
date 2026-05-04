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

@CommandLine.Command(name = "browse", description = "Browse a toolset repository's catalog")
public class ToolSetRepoBrowse extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolSetRepoBrowse.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Parameters(description = "Name of the toolset repository to browse", arity = "1..1")
    private String name;

    @Override
    @SuppressWarnings("unchecked")
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ToolsetReposService service = initAuthenticatedService(ToolsetReposService.class, host);

        try {
            WanakuResponse<Map<String, Object>> response = service.browse(name);
            Map<String, Object> catalog = response.data();

            printer.printInfoMessage(String.format("Repository: %s", catalog.get("name")));
            printer.printInfoMessage(String.format("URL: %s", catalog.get("url")));
            if (catalog.get("icon") != null) {
                printer.printInfoMessage(String.format("Icon: %s", catalog.get("icon")));
            }
            printer.printInfoMessage("");
            printer.printInfoMessage("Available toolsets:");

            List<Map<String, String>> toolsets = (List<Map<String, String>>) catalog.get("toolsets");
            if (toolsets != null) {
                for (Map<String, String> toolset : toolsets) {
                    printer.printInfoMessage(
                            String.format("  %-20s %s", toolset.get("name"), toolset.getOrDefault("description", "")));
                }
            }
        } catch (Exception e) {
            printer.printErrorMessage(String.format("Failed to browse toolset repository: %s", e.getMessage()));
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
