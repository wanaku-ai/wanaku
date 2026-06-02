package ai.wanaku.cli.main.commands.service;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ServiceTemplateService;
import ai.wanaku.core.services.api.ServiceTemplateSummary;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

@CommandLine.Command(name = "list", description = "List all service templates")
public class ServiceTemplateList extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"--search"},
            description = "Optional search term to filter templates",
            arity = "0..1")
    private String search;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ServiceTemplateService service = initService(ServiceTemplateService.class, host);

        try {
            WanakuResponse<List<ServiceTemplateSummary>> response = service.list(search);
            if (response == null) {
                printer.printWarningMessage("No response received from the service%n");
                return EXIT_ERROR;
            }

            List<ServiceTemplateSummary> templates = response.data();

            if (templates == null || templates.isEmpty()) {
                printer.printInfoMessage("No service templates found%n");
                return EXIT_OK;
            }

            printer.printInfoMessage("Available service templates:%n");
            for (ServiceTemplateSummary summary : templates) {
                String name = summary.name() != null ? summary.name() : "unknown";
                String description = summary.description() != null ? summary.description() : "";
                printer.printInfoMessage(String.format(" - %s: %s%n", name, description));
            }

        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }

        return EXIT_OK;
    }
}
