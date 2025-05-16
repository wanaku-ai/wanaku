package ai.wanaku.cli.main.commands.tools;

import java.net.URI;
import java.net.URL;
import java.util.List;

import ai.wanaku.cli.main.converter.URLConverter;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import org.jboss.logging.Logger;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.ToolsService;
import ai.wanaku.core.util.IndexHelper;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ToolHelper.importToolset;

@CommandLine.Command(name = "import",description = "Import a toolset")
public class ToolsImport extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolsImport.class);

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Parameters(description="location to the toolset, can be a local path or an URL", arity = "1..1", converter = URLConverter.class)
    private URL location;

    ToolsService toolsService;

    @Override
    public void run() {
        try {

            List<ToolReference> toolReferences = IndexHelper.loadToolsIndex(location);

            importToolset(toolReferences, host);

        } catch (Exception e) {
            LOG.errorf(e, "Failed to load tools index: %s", e.getMessage());
            throw new RuntimeException(e);
        }
    }



}
