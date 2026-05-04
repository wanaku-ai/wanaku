package ai.wanaku.cli.main.commands.toolset;

import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsetReposService;
import picocli.CommandLine;

@CommandLine.Command(name = "remove", description = "Remove a toolset repository")
public class ToolSetRepoRemove extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolSetRepoRemove.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Parameters(description = "Name of the toolset repository to remove", arity = "1..1")
    private String name;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ToolsetReposService service = initAuthenticatedService(ToolsetReposService.class, host);

        try {
            service.remove(name);
            printer.printSuccessMessage(String.format("Toolset repository '%s' removed", name));
        } catch (Exception e) {
            printer.printErrorMessage(String.format("Failed to remove toolset repository: %s", e.getMessage()));
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
