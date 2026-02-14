package ai.wanaku.cli.main.commands.resources;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ResourcesService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

@Command(name = "remove", description = "Remove exposed resources")
public class ResourcesRemove extends BaseCommand {

    @Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @Option(
            names = {"--name"},
            description = "A human-readable name for the resource. Cannot be used with --label-expression.",
            arity = "0..1")
    private String name;

    @CommandLine.Option(
            names = {"-e", "--label-expression"},
            description = {
                """
                    Remove resources matching a label filter expression. Supports logical operators for complex queries.",
                    For detailed information see the label expression manual page:
                    `wanaku man label-expression`
                    Note: Use --name to remove a single resource by name. The --label-expression,
                    option enables batch removal of multiple resources. Use with caution as this,
                    operation cannot be undone.
            """
            },
            arity = "0..1")
    private String labelExpression;

    ResourcesService resourcesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        resourcesService = initService(ResourcesService.class, host);

        // Validate that either name or labelExpression is provided, but not both
        if (name != null && labelExpression != null) {
            printer.printErrorMessage("Cannot specify both --name and --label-expression. Use one or the other.");
            return EXIT_ERROR;
        }

        if (name == null && labelExpression == null) {
            printer.printErrorMessage("Must specify either --name or --label-expression.");
            return EXIT_ERROR;
        }

        // Handle removal by label expression
        if (labelExpression != null) {
            return removeByLabelExpression(printer);
        }

        // Handle removal by name
        return removeByName(printer);
    }

    private Integer removeByName(WanakuPrinter printer) throws IOException {
        try (Response response = resourcesService.remove(name)) {
            printer.printSuccessMessage("Successfully removed resource reference '" + name + "'");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                String warningMessage = String.format(
                        "Resource not found (%s): %s%n",
                        name, response.getStatusInfo().getReasonPhrase());
                printer.printWarningMessage(warningMessage);
            } else {
                commonResponseErrorHandler(response);
            }
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }

    private Integer removeByLabelExpression(WanakuPrinter printer) throws IOException {
        try {
            // Get all resources matching the label expression
            WanakuResponse<List<ResourceReference>> response = resourcesService.list(labelExpression);
            List<ResourceReference> matchingResources = response.data();

            if (matchingResources == null || matchingResources.isEmpty()) {
                printer.printWarningMessage("No resources found matching label expression: " + labelExpression);
                return EXIT_OK;
            }

            printer.printInfoMessage(String.format(
                    "Found %d resource(s) matching label expression '%s'", matchingResources.size(), labelExpression));

            int successCount = 0;
            int failureCount = 0;

            for (ResourceReference resource : matchingResources) {
                try (Response ignored = resourcesService.remove(resource.getName())) {
                    printer.printSuccessMessage("  Removed: " + resource.getName());
                    successCount++;
                } catch (WebApplicationException ex) {
                    printer.printErrorMessage("  Failed to remove: " + resource.getName());
                    failureCount++;
                }
            }

            printer.printInfoMessage(
                    String.format("Removal complete: %d succeeded, %d failed", successCount, failureCount));

            return failureCount > 0 ? EXIT_ERROR : EXIT_OK;
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
    }
}
