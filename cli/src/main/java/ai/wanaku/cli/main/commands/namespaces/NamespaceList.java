package ai.wanaku.cli.main.commands.namespaces;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.api.types.Namespace;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.NamespacesService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "List namespaces")
public class NamespaceList extends BaseCommand {
    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    NamespacesService namespacesService;

    private AddressableNamespace convertToAddressable(Namespace n) {
        return AddressableNamespace.fromNamespace(host, n);
    }

    private static class AddressableNamespace extends Namespace {
        private String host;

        @Override
        public String getPath() {
            return String.format("%s/%s/mcp/sse", host, super.getPath());
        }

        public static AddressableNamespace fromNamespace(String host, Namespace namespace) {
            AddressableNamespace ret = new AddressableNamespace();
            ret.setId(namespace.getId());
            ret.setPath(namespace.getPath());
            ret.setName(namespace.getName() == null ? "" : namespace.getName());
            ret.host = host;

            return ret;
        }

        public static AddressableNamespace defaultNs(String host) {
            AddressableNamespace ret = new AddressableNamespace();
            ret.setId("<default>");
            ret.setPath("");
            ret.setName("");
            ret.host = host;

            return ret;
        }
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        namespacesService =
                QuarkusRestClientBuilder.newBuilder().baseUri(URI.create(host)).build(NamespacesService.class);

        try {
            WanakuResponse<List<Namespace>> response = namespacesService.list();
            List<AddressableNamespace> list =
                    response.data().stream().map(this::convertToAddressable).collect(Collectors.toList());

            list.add(AddressableNamespace.defaultNs(host));

            printer.printTable(list, "id", "name", "path");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
