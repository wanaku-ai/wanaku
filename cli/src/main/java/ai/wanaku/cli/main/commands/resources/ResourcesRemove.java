package ai.wanaku.cli.main.commands.resources;

import java.net.URI;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.ResourcesService;
import picocli.CommandLine;

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

        resourcesService.remove(name);
    }
}
