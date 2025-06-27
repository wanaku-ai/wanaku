package ai.wanaku.cli.main.commands.namespaces;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.Namespace;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.NamespacesService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.net.URI;
import java.util.List;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

@CommandLine.Command(name = "list", description = "List namespaces")
public class NamespaceList extends BaseCommand {
    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    NamespacesService namespacesService;

    @Override
    public Integer call() throws Exception {
        namespacesService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(NamespacesService.class);


        try (Terminal terminal = WanakuPrinter.terminalInstance() ){
            WanakuPrinter printer = new WanakuPrinter(null, terminal);
            WanakuResponse<List<Namespace>> response = namespacesService.list();
            List<Namespace> list = response.data();
            printer.printTable(list, "id","name","path");
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
