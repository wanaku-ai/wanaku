package ai.wanaku.cli.main.commands.tools;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.cli.main.converter.URLConverter;
import ai.wanaku.cli.main.services.ToolsService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.swagger.v3.oas.models.OpenAPI;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static ai.wanaku.cli.main.support.ToolHelper.importToolset;
import static ai.wanaku.cli.main.support.ToolsGenerateHelper.*;

@CommandLine.Command(name = "generate", description = "generate tools from an OpenApi specification")
public class ToolsGenerate implements Callable<Integer> {

    private static final Logger LOG = Logger.getLogger(ToolsGenerate.class);



    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display the help and sub-commands")
    private boolean helpRequested = false;

    @CommandLine.Option(names = {"--server-url", "-u"}, description = "Override the OpenAPI server base url", arity = "0..1")
    protected String serverUrl;

    @CommandLine.Option(names = {"--output-file", "-o"}, description = "path of the file where the toolset will be written. If not set, the toolset will be written to the STDOUT", arity = "0..1")
    private String outputFile;

    @CommandLine.Option(names = {"--server-index", "-i"}, description = "index of the server in the server object list in the OpenApi Spec. 0 is the first one.", arity = "0..1")
    private Integer serverIndex;

    @CommandLine.Option(names = {"--server-variable", "-v"}, description = "key-value pair for server object variable, can be specified multiple times. Ex  '-v environment=prod -v region=us-east'", arity = "0..*")
    private Map<String, String> serverVariables = new HashMap<>();

    @CommandLine.Option(names = {"--import", "-I"}, arity = "0", description = "Import the generated toolset in the registry")
    private boolean importToolset;

    @CommandLine.Option(names = {"--import-service-host", "-H"}, description = "The API host used to import the generated toolset ", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Parameters(description = "location to the OpenAPI spec definition, can be a local path or an URL", arity = "1..1", converter = URLConverter.class)
    private URL specLocation;


    @Override
    public Integer call() {
        try {
            // Load and resolve OpenAPI Spec
            OpenAPI openAPI = loadAndResolveOpenAPI(specLocation.toString());

            if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
                LOG.warn("No paths found in the OpenAPI specification");
                return 3;
            }

            // Determine base URL to use
            String baseUrl = determineBaseUrl(openAPI, serverUrl, serverIndex, serverVariables);

            // Generate tool references
            List<ToolReference> toolReferences = generateToolReferences(openAPI, baseUrl);

            // Write output
            writeOutput(toolReferences, outputFile);

            if (importToolset) {
                System.err.print("\n\nImporting toolset...");
                importToolset(toolReferences, host);
                System.err.print("Done.");
            }
        } catch (Exception e) {
            System.err.println("Error processing OpenAPI specification: " + e.getMessage());
            return 2;
        }
        return 0;
    }
}