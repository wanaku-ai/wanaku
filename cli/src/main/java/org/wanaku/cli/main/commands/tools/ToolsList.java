package org.wanaku.cli.main.commands.tools;

import java.net.URI;
import java.util.List;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import org.jboss.logging.Logger;
import org.wanaku.api.types.ToolReference;
import org.wanaku.cli.main.commands.BaseCommand;
import org.wanaku.cli.main.services.ToolsService;
import org.wanaku.cli.main.support.PrettyPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "list",description = "List tools")
public class ToolsList extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolsList.class);

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    ToolsService toolsService;

    @Override
    public void run() {
        toolsService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(ToolsService.class);

        List<ToolReference> list = toolsService.list();
        PrettyPrinter.printTools(list);
    }

}
