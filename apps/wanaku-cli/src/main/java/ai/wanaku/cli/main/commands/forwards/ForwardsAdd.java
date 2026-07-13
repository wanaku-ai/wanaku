package ai.wanaku.cli.main.commands.forwards;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import org.jline.terminal.Terminal;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ForwardsService;
import ai.wanaku.core.services.api.NamespacesService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

@Command(name = "add", description = "Add forward targets")
public class ForwardsAdd extends BaseCommand {

    private static final String ERROR_NO_NAMESPACE_MATCH =
            "No namespace matched '%s'. Use 'wanaku namespace list' to see available namespaces.";

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

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    NamespaceOptions namespaceOptions;

    static class NamespaceOptions {
        @Option(
                names = {"-N", "--namespace"},
                description = "The namespace ID associated with the tool")
        String namespace;

        @Option(
                names = {"--namespace-name"},
                description = "The namespace name to use (looked up automatically)")
        String namespaceName;
    }

    private String resolveNamespaceId() throws Exception {
        if (namespaceOptions.namespace != null && !namespaceOptions.namespace.isBlank()) {
            return namespaceOptions.namespace;
        }

        NamespacesService namespacesService =
                QuarkusRestClientBuilder.newBuilder().baseUri(URI.create(host)).build(NamespacesService.class);

        try {
            WanakuResponse<List<Namespace>> response = namespacesService.list();
            List<Namespace> matches = response.data().stream()
                    .filter(n -> namespaceOptions.namespaceName.equals(n.getName()))
                    .collect(Collectors.toList());

            if (matches.isEmpty()) {
                throw new IllegalArgumentException(ERROR_NO_NAMESPACE_MATCH.formatted(namespaceOptions.namespaceName));
            }
            if (matches.size() > 1) {
                throw new IllegalStateException("Multiple namespaces matched '%s'. Use --namespace with the ID instead."
                        .formatted(namespaceOptions.namespaceName));
            }
            return matches.getFirst().getId();
        } catch (WebApplicationException ex) {
            commonResponseErrorHandler(ex.getResponse());
            throw ex;
        }
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ForwardsService forwardsService = initAuthenticatedService(ForwardsService.class, host);

        String namespaceId = resolveNamespaceId();

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
