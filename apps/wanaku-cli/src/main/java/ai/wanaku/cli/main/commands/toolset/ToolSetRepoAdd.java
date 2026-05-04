package ai.wanaku.cli.main.commands.toolset;

import java.util.Map;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsetReposService;
import picocli.CommandLine;

@CommandLine.Command(name = "add", description = "Add a toolset repository")
public class ToolSetRepoAdd extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolSetRepoAdd.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"-n", "--name"},
            description = "Name for the toolset repository",
            required = true)
    private String name;

    @CommandLine.Option(
            names = {"-u", "--url"},
            description = "Base URL of the toolset repository",
            required = true)
    private String url;

    @CommandLine.Option(
            names = {"-d", "--description"},
            description = "Description of the toolset repository")
    private String description;

    @CommandLine.Option(
            names = {"--icon"},
            description = "Icon for the toolset repository")
    private String icon;

    @CommandLine.Option(
            names = {"-b", "--branch"},
            description = "Git branch to use",
            defaultValue = "main")
    private String branch;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ToolsetReposService service = initAuthenticatedService(ToolsetReposService.class, host);

        Map<String, String> repo = new java.util.HashMap<>();
        repo.put("name", name);
        repo.put("url", url);
        repo.put("branch", branch);
        if (description != null) {
            repo.put("description", description);
        }
        if (icon != null) {
            repo.put("icon", icon);
        }

        try {
            service.add(repo);
            printer.printSuccessMessage(String.format("Toolset repository '%s' added successfully", name));
        } catch (Exception e) {
            printer.printErrorMessage(String.format("Failed to add toolset repository: %s", e.getMessage()));
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
