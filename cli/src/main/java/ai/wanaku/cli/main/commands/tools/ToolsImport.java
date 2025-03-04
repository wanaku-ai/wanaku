package ai.wanaku.cli.main.commands.tools;

import java.io.File;
import java.net.URI;
import java.util.List;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import org.jboss.logging.Logger;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.ToolsService;
import ai.wanaku.core.util.IndexHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "import",description = "Import a toolset")
public class ToolsImport extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolsImport.class);

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;


    @CommandLine.Parameters(description="Path to the toolset", arity = "1..1")
    private String path;

    ToolsService toolsService;

    @Override
    public void run() {
        try {
            toolsService = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(URI.create(host))
                    .build(ToolsService.class);

            File indexFile = new File(path);
            List<ToolReference> toolReferences = IndexHelper.loadToolsIndex(indexFile);

            for (var toolReference : toolReferences) {
                toolsService.add(toolReference);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to load tools index: %s", e.getMessage());
            throw new RuntimeException(e);
        }




    }

}
