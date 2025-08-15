package ai.wanaku.cli.main.commands.tools;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.api.types.InputSchema;
import ai.wanaku.api.types.Property;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.io.ToolPayload;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.FileHelper;
import ai.wanaku.cli.main.support.PropertyHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(name = "add", description = "Add tools")
public class ToolsAdd extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolsAdd.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"-n", "--name"},
            description = "Name of the tool",
            required = true)
    private String name;

    @CommandLine.Option(
            names = {"-N", "--namespace"},
            description = "The namespace associated with the tool",
            defaultValue = "",
            required = true)
    private String namespace;

    @CommandLine.Option(
            names = {"-d", "--description"},
            description = "Description of the tool",
            required = true)
    private String description;

    @CommandLine.Option(
            names = {"-u", "--uri"},
            description = "URI of the tool",
            required = true)
    private String uri;

    @CommandLine.Option(
            names = {"--type"},
            description = "Type of the tool (i.e: http)",
            required = true)
    private String type;

    @CommandLine.Option(
            names = {"--input-schema-type"},
            description = "Type of input schema (e.g., 'string', 'object')",
            defaultValue = "object")
    private String inputSchemaType;

    @CommandLine.Option(
            names = {"-p", "--property"},
            description = "Property name and value (e.g., '--property name:type,description)")
    private List<String> properties;

    @CommandLine.Option(
            names = {"-r", "--required"},
            description = "List of required property names (e.g., '-r foo bar')",
            arity = "0..*")
    private List<String> required;

    @CommandLine.Option(
            names = {"--configuration-from-file"},
            description = "Configure the capability provider using the given file",
            arity = "0..1")
    private String configurationFromFile;

    @CommandLine.Option(
            names = {"--secrets-from-file"},
            description = "Add the given secrets to the capability provider using the given file",
            arity = "0..1")
    private String secretsFromFile;

    ToolsService toolsService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {

        ToolReference toolReference = new ToolReference();
        toolReference.setName(name);
        toolReference.setDescription(description);
        toolReference.setUri(uri);
        toolReference.setType(type);
        toolReference.setNamespace(namespace);

        InputSchema inputSchema = new InputSchema();
        inputSchema.setType(inputSchemaType);

        if (properties != null) {
            for (String propertyStr : properties) {
                PropertyHelper.PropertyDescription result = PropertyHelper.parseProperty(propertyStr);

                Property property = new Property();
                property.setType(result.dataType());
                property.setDescription(result.description());

                inputSchema.getProperties().put(result.propertyName(), property);
            }
        }

        toolReference.setInputSchema(inputSchema);

        if (required != null) {
            inputSchema.setRequired(required);
        }

        ToolPayload toolPayload = new ToolPayload();
        toolPayload.setPayload(toolReference);

        FileHelper.loadConfigurationSources(configurationFromFile, toolPayload::setConfigurationData);
        FileHelper.loadConfigurationSources(secretsFromFile, toolPayload::setSecretsData);

        toolsService = initService(ToolsService.class, host);

        try {
            WanakuResponse<ToolReference> data = toolsService.addWithPayload(toolPayload);

        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                System.err.printf(
                        "There is no downstream service capable of handling requests of the given type (%s): %s%n",
                        type, response.getStatusInfo().getReasonPhrase());

                System.exit(1);
            } else {
                commonResponseErrorHandler(response);
                return EXIT_ERROR;
            }
        }
        return EXIT_OK;
    }
}
