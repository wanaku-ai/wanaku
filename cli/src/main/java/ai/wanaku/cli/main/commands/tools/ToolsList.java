package ai.wanaku.cli.main.commands.tools;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.WanakuResponse;
import java.net.URI;
import java.util.List;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import org.jboss.logging.Logger;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.ToolsService;
import ai.wanaku.cli.main.support.PrettyPrinter;
import org.jboss.resteasy.reactive.RestResponse;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

@CommandLine.Command(name = "list",description = "List tools")
public class ToolsList extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolsList.class);

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    ToolsService toolsService;

    @Override
    public void run() {
        toolsService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(ToolsService.class);


        try (RestResponse<WanakuResponse<List<ToolReference>>> response = toolsService.list()) {

            List<ToolReference> list = response.getEntity().data();
            PrettyPrinter.printTools(list);
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
        }

    }

}
