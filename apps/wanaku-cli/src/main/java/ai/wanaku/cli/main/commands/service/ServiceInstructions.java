package ai.wanaku.cli.main.commands.service;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.DeploymentInstructions;
import ai.wanaku.core.services.api.ServiceCatalogService;
import ai.wanaku.core.services.api.SystemInstruction;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

@CommandLine.Command(name = "instructions", description = "Get deployment instructions for a service catalog")
public class ServiceInstructions extends BaseCommand {

    @CommandLine.Option(
            names = {"--name"},
            description = "The service catalog name",
            required = true)
    private String name;

    @CommandLine.Option(
            names = {"--model"},
            description = "Deployment model: local, docker, kubernetes",
            required = true)
    private String model;

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ServiceCatalogService service = initAuthenticatedService(ServiceCatalogService.class, host);

        try {
            WanakuResponse<DeploymentInstructions> response = service.getDeploymentInstructions(name, model);
            DeploymentInstructions instructions = response.data();

            if (instructions == null) {
                printer.printErrorMessage("No deployment instructions returned");
                return EXIT_ERROR;
            }

            printer.printInfoMessage(String.format(
                    "Deployment instructions for '%s' (%s, %s)",
                    instructions.catalogName(), instructions.catalogType(), instructions.deploymentModel()));

            for (SystemInstruction sys : instructions.systems()) {
                if (!"all".equals(sys.systemName())) {
                    printer.printInfoMessage(String.format("--- System: %s ---", sys.systemName()));
                }
                System.out.println(sys.instruction());
                System.out.println();
            }

            if (instructions.placeholders() != null
                    && !instructions.placeholders().isEmpty()) {
                printer.printInfoMessage("Placeholders to fill:");
                instructions
                        .placeholders()
                        .forEach(p -> printer.printInfoMessage(String.format(
                                "  <%s> - %s%s",
                                p.key(),
                                p.description(),
                                p.defaultValue() != null && !p.defaultValue().isEmpty()
                                        ? " (default: " + p.defaultValue() + ")"
                                        : "")));
            }
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }

        return EXIT_OK;
    }
}
