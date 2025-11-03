package ai.wanaku.cli.main.commands.tools;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.logging.Logger;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Command to list tools registered in the Wanaku platform.
 * <p>
 * This command retrieves and displays all registered tools, optionally filtered
 * by label expressions. The output includes tool name, namespace, type, labels,
 * and URI for each tool.
 * </p>
 * <p>
 * Label expressions support logical operators (AND, OR, NOT) for complex filtering.
 * See the label expression manual page for detailed syntax information.
 * </p>
 */
@CommandLine.Command(name = "list", description = "List tools")
public class ToolsList extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolsList.class);

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"-e", "--label-expression"},
            description = {
                """
Filter tools by label expression. Supports logical operators for complex queries.
For detailed information see tha label expression manual page:
`wanaku man label-expression`
Note: If omitted, all tools are listed. Label matching is case-sensitive.
"""
            })
    private String labelExpression;

    ToolsService toolsService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        toolsService = initService(ToolsService.class, host);
        try {
            WanakuResponse<List<ToolReference>> response = toolsService.list(labelExpression);
            List<ToolReference> list = response.data();
            list.stream().filter(t -> t.getNamespace() == null).forEach(t -> t.setNamespace("default"));
            printer.printTable(list, "name", "namespace", "type", "uri", "labels");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
