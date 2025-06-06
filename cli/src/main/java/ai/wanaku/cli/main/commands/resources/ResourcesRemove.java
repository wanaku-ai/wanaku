package ai.wanaku.cli.main.commands.resources;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.net.URI;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.ResourcesService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

@CommandLine.Command(name = "remove",description = "Remove exposed resources")
public class ResourcesRemove extends BaseCommand {

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(names = { "--name" }, description = "A human-readable name for the resource", required = true, arity = "0..1")
    private String name;

    ResourcesService resourcesService;

    @Override
    public void run() {
        resourcesService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(ResourcesService.class);

        try (Response response = resourcesService.remove(name)) {

        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                System.err.printf("Resource not found (%s): %s%n",
                        name, response.getStatusInfo().getReasonPhrase());

                System.exit(1);
            } else {
                commonResponseErrorHandler(response);
            }
        }

    }
}
