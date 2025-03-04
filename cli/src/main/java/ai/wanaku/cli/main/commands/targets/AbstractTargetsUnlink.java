package ai.wanaku.cli.main.commands.targets;

import java.net.URI;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.LinkService;
import picocli.CommandLine;

@CommandLine.Command(name = "unlink",
        description = "Unlink services from a target")
public abstract class AbstractTargetsUnlink extends BaseCommand {
    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(names = { "--service" }, description = "The service to link", required = true, arity = "0..1")
    protected String service;

    protected LinkService linkService;

    protected void initService() {
        linkService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(LinkService.class);
    }
}
