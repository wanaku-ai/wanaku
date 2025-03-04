package ai.wanaku.cli.main.commands.targets;

import java.net.URI;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.LinkService;
import picocli.CommandLine;

public abstract class AbstractTargetsLink extends BaseCommand {
    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(names = { "--service" }, description = "The service to link", required = true, arity = "0..1")
    protected String service;

    @CommandLine.Option(names = { "--target" }, description = "The target to link (i.e.: localhost:9000)", required = true, arity = "0..1")
    protected String target;

    protected LinkService linkService;

    protected void initService() {
        linkService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(LinkService.class);
    }
}
