package ai.wanaku.cli.main.commands.forwards;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.NamespaceOptions;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ForwardRequest;
import ai.wanaku.core.services.api.ForwardsService;
import ai.wanaku.core.services.api.NamespacesService;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.ArgGroup;
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

    @Option(
            names = {"--root"},
            description = "MCP root in name=uri format (e.g. --root myroot=file:///path/to/dir). "
                    + "Repeatable for multiple roots.",
            arity = "0..*")
    protected List<String> rootEntries;

    @ArgGroup(exclusive = true, multiplicity = "1")
    NamespaceOptions namespaceOptions;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ForwardsService forwardsService = initAuthenticatedService(ForwardsService.class, host);

        NamespacesService namespacesService = initAuthenticatedService(NamespacesService.class, host);
        String namespaceId = namespaceOptions.resolveNamespaceId(namespacesService);

        ForwardRequest request = new ForwardRequest();
        request.setName(name);
        request.setAddress(service);
        request.setNamespace(namespaceId);
        request.setRoots(parseRootEntries());

        try {
            forwardsService.addForward(request);
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }

    private Map<String, String> parseRootEntries() {
        if (rootEntries == null || rootEntries.isEmpty()) {
            return null;
        }
        Map<String, String> roots = new LinkedHashMap<>();
        for (String entry : rootEntries) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq >= entry.length() - 1) {
                throw new IllegalArgumentException("Invalid root format: '%s'. Expected name=uri".formatted(entry));
            }
            roots.put(entry.substring(0, eq), entry.substring(eq + 1));
        }
        return roots;
    }
}
