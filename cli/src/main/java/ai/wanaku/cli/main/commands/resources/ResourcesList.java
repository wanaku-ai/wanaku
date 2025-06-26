package ai.wanaku.cli.main.commands.resources;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.WanakuResponse;
import java.net.URI;
import java.util.List;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.core.services.api.ResourcesService;
import ai.wanaku.cli.main.support.PrettyPrinter;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

@CommandLine.Command(name = "list", description = "List resources")
public class ResourcesList extends BaseCommand {

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    ResourcesService resourcesService;

    @Override
    public Integer call() {
        resourcesService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(ResourcesService.class);


        try {
            WanakuResponse<List<ResourceReference>> response = resourcesService.list();
            List<ResourceReference> list = response.data();
            PrettyPrinter.printResources(list);
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
