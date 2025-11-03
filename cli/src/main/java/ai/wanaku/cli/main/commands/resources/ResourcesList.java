package ai.wanaku.cli.main.commands.resources;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ResourcesService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jline.terminal.Terminal;

/**
 * Command to list resources registered in the Wanaku platform.
 * <p>
 * This command retrieves and displays all registered resources, optionally filtered
 * by label expressions. The output includes resource name, type, description,
 * and location for each resource.
 * </p>
 * <p>
 * Label expressions support logical operators (AND, OR, NOT) for complex filtering.
 * See the label expression manual page for detailed syntax information.
 * </p>
 */
@Command(name = "list", description = "List resources")
public class ResourcesList extends BaseCommand {

    @Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @Option(
            names = {"-e", "--label-expression"},
            description = {
                """
Filter resources by label expression. Supports logical operators for complex queries.
For detailed information see the label expression manual page:
`wanaku man label-expression`
Note: If omitted, all resources are listed. Label matching is case-sensitive.
"""
            })
    private String labelExpression;

    ResourcesService resourcesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        resourcesService = initService(ResourcesService.class, host);
        try {
            WanakuResponse<List<ResourceReference>> response = resourcesService.list(labelExpression);
            List<ResourceReference> list = response.data();
            printer.printTable(list, "name", "type", "description", "location", "labels");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
