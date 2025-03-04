package ai.wanaku.cli.main.commands.targets;

import java.net.URI;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.LinkService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import picocli.CommandLine;

public abstract class AbstractTargetsConfigure extends BaseCommand {
    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(names = { "--service" }, description = "The service to link", required = true, arity = "0..1")
    protected String service;

    @CommandLine.Option(names = { "--option" }, description = "The option to set", required = true, arity = "0..1")
    protected String option;

    @CommandLine.Option(names = { "--value" }, description = "The value to set the option", required = true, arity = "0..1")
    protected String value;

    protected LinkService linkService;

    protected void initService() {
        linkService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(LinkService.class);
    }
}
