package ai.wanaku.cli.main.commands.resources;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.CommandHelper;
import ai.wanaku.cli.main.support.LabelHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ResourcesService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;

@CommandLine.Command(name = "remove", description = "Remove exposed resources")
public class ResourcesRemove extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
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

    @CommandLine.Option(
            names = {"-y", "--assume-yes"},
            description = "automatically answer yes for all questions")
    private boolean assumeYes;

    ResourcesService resourcesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        resourcesService = initService(ResourcesService.class, host);

        int validationResult = LabelHelper.validateLabelExpression(name, labelExpression, "--name", printer);
        if (validationResult != EXIT_OK) {
            return validationResult;
        }

        if (labelExpression != null) {
            return removeByLabelExpression(terminal, printer);
        }

        return removeByName(printer);
    }

    private Integer removeByName(WanakuPrinter printer) throws IOException {
        try {
            resourcesService.remove(name);
            printer.printSuccessMessage("Successfully removed resource reference '" + name + "'");
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Resource", name, printer);
        }
        return EXIT_OK;
    }

    private Integer removeByLabelExpression(Terminal terminal, WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<List<ResourceReference>> response = resourcesService.list(labelExpression);
            List<ResourceReference> matchingResources = response.data();

            if (matchingResources == null || matchingResources.isEmpty()) {
                printer.printWarningMessage("No resources found matching label expression: " + labelExpression);
                return EXIT_OK;
            }

            printer.printInfoMessage(String.format(
                    "Found %d resource(s) matching label expression '%s'", matchingResources.size(), labelExpression));

            printer.printTable(matchingResources, "name", "type", "location", "labels");

            boolean continues = true;
            if (!assumeYes) {
                continues = CommandHelper.confirm(terminal, "Do you want to remove all the resources above?");
            }

            if (!continues) {
                return EXIT_OK;
            }

            int successCount = 0;
            int failureCount = 0;

            for (ResourceReference resource : matchingResources) {
                try {
                    resourcesService.remove(resource.getName());
                    printer.printSuccessMessage(" Removed: " + resource.getName());
                    successCount++;
                } catch (WebApplicationException ex) {
                    printer.printErrorMessage(" Failed to remove: " + resource.getName());
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
