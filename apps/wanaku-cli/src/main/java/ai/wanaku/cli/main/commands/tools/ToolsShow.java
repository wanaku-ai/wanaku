package ai.wanaku.cli.main.commands.tools;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import io.quarkus.runtime.annotations.RegisterForReflection;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

/**
 * Command to show detailed information about a specific tool registered in the Wanaku platform.
 * <p>
 * This command retrieves and displays comprehensive details about a tool including
 * its name, description, type, namespace, URI, labels, and input schema properties.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * <pre>{@code
 * wanaku tools show my-tool
 * wanaku tools show --host http://remote:8080 my-tool
 * }</pre>
 */
@CommandLine.Command(name = "show", description = "Show detailed information about a specific tool")
public class ToolsShow extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Parameters(description = "The name of the tool to show", arity = "1")
    private String toolName;

    ToolsService toolsService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        toolsService = initService(ToolsService.class, host);
        try {
            WanakuResponse<ToolReference> response = toolsService.getByName(toolName);
            ToolReference tool = response.data();

            if (tool == null) {
                printer.printWarningMessage("Tool not found: " + toolName);
                return EXIT_ERROR;
            }

            // Create display record with default namespace for display purposes
            String displayNamespace = tool.getNamespace() != null ? tool.getNamespace() : "default";
            ToolDisplay toolDisplay = new ToolDisplay(
                    tool.getName(),
                    displayNamespace,
                    tool.getType(),
                    tool.getDescription(),
                    tool.getUri(),
                    tool.getLabels());

            // Print basic tool information
            printer.printInfoMessage("Tool Details:");
            printer.printAsMap(toolDisplay, "name", "namespace", "type", "description", "uri", "labels");

            // Print input schema properties if available
            InputSchema inputSchema = tool.getInputSchema();
            if (inputSchema != null
                    && inputSchema.getProperties() != null
                    && !inputSchema.getProperties().isEmpty()) {
                printer.printInfoMessage("\nInput Schema Properties:");
                List<PropertyDisplay> properties = new ArrayList<>();
                List<String> required = inputSchema.getRequired() != null ? inputSchema.getRequired() : List.of();

                for (Map.Entry<String, Property> entry :
                        inputSchema.getProperties().entrySet()) {
                    Property prop = entry.getValue();
                    properties.add(new PropertyDisplay(
                            entry.getKey(),
                            prop.getType(),
                            prop.getDescription(),
                            required.contains(entry.getKey()) ? "yes" : "no"));
                }
                printer.printTable(properties, "name", "type", "description", "required");
            }

            return EXIT_OK;
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
    }

    /**
     * Helper record to display tool information without mutating the API object.
     */
    @RegisterForReflection
    public record ToolDisplay(
            String name, String namespace, String type, String description, String uri, Map<String, String> labels) {}

    /**
     * Helper record to display input schema properties in a table format.
     */
    @RegisterForReflection
    public record PropertyDisplay(String name, String type, String description, String required) {}
}
