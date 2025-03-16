package ai.wanaku.cli.main.commands.targets;

import java.net.URI;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.LinkService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import picocli.CommandLine;

@CommandLine.Command(name = "state",
        description = "Get the state of the targeted services")
public abstract class AbstractTargetState extends BaseCommand {
    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    protected LinkService linkService;

    protected void initService() {
        linkService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(LinkService.class);
    }

}
