package ai.wanaku.cli.main.commands.resources;

import java.net.URI;
import java.util.List;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.ResourcesService;
import ai.wanaku.cli.main.support.PrettyPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "list",description = "List resources")
public class ResourcesList extends BaseCommand {

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    ResourcesService resourcesService;

    @Override
    public void run() {
        resourcesService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(ResourcesService.class);

        List<ResourceReference> list = resourcesService.list();
        PrettyPrinter.printResources(list);
    }
}
