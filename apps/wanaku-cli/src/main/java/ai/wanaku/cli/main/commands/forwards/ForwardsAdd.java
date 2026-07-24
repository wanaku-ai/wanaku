package ai.wanaku.cli.main.commands.forwards;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.NamespaceOptions;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ForwardsService;
import ai.wanaku.core.services.api.NamespacesService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

@Command(name = "add", description = "Add forward targets")
public class ForwardsAdd extends BaseCommand {

    @Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @Option(
            names = {"--name"},
            description = "The name of the service to forward",
            required = true,
            arity = "0..1")
    protected String name;

    @Option(
            names = {"--service"},
            description = "The service to forward",
            required = true,
            arity = "0..1")
    protected String service;

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    NamespaceOptions namespaceOptions;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ForwardsService forwardsService = initAuthenticatedService(ForwardsService.class, host);

        String namespaceId = null;
        if (namespaceOptions != null) {
            NamespacesService namespacesService = initAuthenticatedService(NamespacesService.class, host);
            namespaceId = namespaceOptions.resolveNamespaceId(namespacesService);
        }

        ForwardReference reference = new ForwardReference();
        reference.setName(name);
        reference.setAddress(service);
        reference.setNamespace(namespaceId);

        try {
            forwardsService.addForward(reference);
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
