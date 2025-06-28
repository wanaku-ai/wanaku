package ai.wanaku.cli.main.commands.tools;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.converter.URLConverter;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsService;
import ai.wanaku.core.util.ToolsetIndexHelper;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

import java.net.URL;
import java.util.List;

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
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            List<ToolReference> toolReferences = ToolsetIndexHelper.loadToolsIndex(location);
            importToolset(toolReferences, host);
        } catch (Exception e) {
            printer.printErrorMessage(String.format("Failed to load tools index: %s", e.getMessage()));
            throw new RuntimeException(e);
        }
        return EXIT_OK;
    }
}
