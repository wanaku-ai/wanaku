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
import ai.wanaku.cli.main.support.NameSelector;
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

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    NameSelector selector;

    @CommandLine.Option(
            names = {"-y", "--assume-yes"},
            description = "automatically answer yes for all questions")
    private boolean assumeYes;

    ResourcesService resourcesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        resourcesService = initAuthenticatedService(ResourcesService.class, host);

        if (selector.labelExpression != null) {
            return removeByLabelExpression(terminal, printer);
        }

        return removeByName(printer);
    }

    private Integer removeByName(WanakuPrinter printer) throws IOException {
        try {
            resourcesService.remove(selector.name);
            printer.printSuccessMessage("Successfully removed resource reference '" + selector.name + "'");
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Resource", selector.name, printer);
        }
        return EXIT_OK;
    }

    private Integer removeByLabelExpression(Terminal terminal, WanakuPrinter printer) throws IOException {
        try {
            WanakuResponse<List<ResourceReference>> response = resourcesService.list(selector.labelExpression);
            List<ResourceReference> matchingResources = response.data();

            if (matchingResources == null || matchingResources.isEmpty()) {
                printer.printWarningMessage(
                        "No resources found matching label expression: " + selector.labelExpression);
                return EXIT_OK;
            }

            printer.printInfoMessage(String.format(
                    "Found %d resource(s) matching label expression '%s'",
                    matchingResources.size(), selector.labelExpression));

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
