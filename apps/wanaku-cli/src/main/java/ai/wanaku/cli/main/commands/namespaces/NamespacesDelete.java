package ai.wanaku.cli.main.commands.namespaces;

import jakarta.ws.rs.WebApplicationException;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.NamespacesService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.handleNotFound;

@CommandLine.Command(name = "delete", description = "Delete a namespace by id")
public class NamespacesDelete extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Parameters(description = "The ID of the namespace to delete", arity = "1")
    String id;

    NamespacesService namespacesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        namespacesService = initServiceIfNeeded(namespacesService, NamespacesService.class, host);

        try {
            namespacesService.delete(id);
            printer.printSuccessMessage("Namespace deleted: " + id);
            return EXIT_OK;
        } catch (WebApplicationException ex) {
            return handleNotFound(ex, "Namespace", id, printer);
        }
    }
}
