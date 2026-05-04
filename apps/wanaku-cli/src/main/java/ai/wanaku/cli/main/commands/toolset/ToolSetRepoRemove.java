package ai.wanaku.cli.main.commands.toolset;

import jakarta.ws.rs.core.Response;

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
            Response response = service.remove(name);
            int status = response.getStatus();
            if (status == Response.Status.NOT_FOUND.getStatusCode()) {
                printer.printErrorMessage(String.format("Toolset repository '%s' not found", name));
                return EXIT_ERROR;
            }
            if (status >= 200 && status < 300) {
                printer.printSuccessMessage(String.format("Toolset repository '%s' removed", name));
            } else {
                printer.printErrorMessage(String.format(
                        "Failed to remove toolset repository '%s': server returned status %d", name, status));
                return EXIT_ERROR;
            }
        } catch (Exception e) {
            printer.printErrorMessage(String.format("Failed to remove toolset repository: %s", e.getMessage()));
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
