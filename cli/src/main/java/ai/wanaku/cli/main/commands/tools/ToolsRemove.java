package ai.wanaku.cli.main.commands.tools;

import java.net.URI;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import org.jboss.logging.Logger;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.ToolsService;
import picocli.CommandLine;

@CommandLine.Command(name = "remove",description = "Remove tools")
public class ToolsRemove extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolsRemove.class);

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(names = {"-n", "--name"}, description="Name of the tool to remove", required = true)
    private String name;

    ToolsService toolsService;

    @Override
    public void run() {
        toolsService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(ToolsService.class);

        toolsService.remove(name);
    }

}
