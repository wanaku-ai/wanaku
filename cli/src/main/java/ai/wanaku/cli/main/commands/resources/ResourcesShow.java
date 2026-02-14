package ai.wanaku.cli.main.commands.resources;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import io.quarkus.runtime.annotations.RegisterForReflection;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ResourcesService;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

/**
 * Command to show detailed information about a specific resource registered in the Wanaku platform.
 * <p>
 * This command retrieves and displays comprehensive details about a resource including
 * its name, description, type, location, MIME type, namespace, labels, and parameters.
 * </p>
 * <p>
 * Example usage:
 * </p>
 * <pre>{@code
 * wanaku resources show my-resource
 * wanaku resources show --host http://remote:8080 my-resource
 * }</pre>
 */
@Command(name = "show", description = "Show detailed information about a specific resource")
public class ResourcesShow extends BaseCommand {

    @Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @Parameters(description = "The name of the resource to show", arity = "1")
    private String resourceName;

    ResourcesService resourcesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        resourcesService = initService(ResourcesService.class, host);
        try {
            WanakuResponse<ResourceReference> response = resourcesService.getByName(resourceName);
            ResourceReference resource = response.data();

            if (resource == null) {
                printer.printWarningMessage("Resource not found: " + resourceName);
                return EXIT_ERROR;
            }

            // Create display record with default namespace for display purposes
            String displayNamespace = resource.getNamespace() != null ? resource.getNamespace() : "default";
            ResourceDisplay resourceDisplay = new ResourceDisplay(
                    resource.getName(),
                    resource.getType(),
                    resource.getDescription(),
                    resource.getLocation(),
                    resource.getMimeType(),
                    displayNamespace,
                    resource.getLabels());

            // Print basic resource information
            printer.printInfoMessage("Resource Details:");
            printer.printAsMap(
                    resourceDisplay, "name", "type", "description", "location", "mimeType", "namespace", "labels");

            // Print parameters if available
            List<ResourceReference.Param> params = resource.getParams();
            if (params != null && !params.isEmpty()) {
                printer.printInfoMessage("\nParameters:");
                printer.printTable(params, "name", "value");
            }

            return EXIT_OK;
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
    }

    /**
     * Helper record to display resource information without mutating the API object.
     */
    @RegisterForReflection
    public record ResourceDisplay(
            String name,
            String type,
            String description,
            String location,
            String mimeType,
            String namespace,
            Map<String, String> labels) {}
}
