package ai.wanaku.cli.main.commands.service;

import jakarta.ws.rs.WebApplicationException;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ServiceCatalogService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;

@CommandLine.Command(name = "remove", description = "Remove a service catalog by name")
public class ServiceCatalogRemove extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"--name"},
            description = "Name of the service catalog to remove",
            required = true,
            arity = "0..1")
    private String name;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ServiceCatalogService service = initAuthenticatedService(ServiceCatalogService.class, host);

        try {
            service.remove(name);
            printer.printSuccessMessage("Successfully removed service catalog '" + name + "'");
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Service catalog", name, printer);
        }

        return EXIT_OK;
    }
}
