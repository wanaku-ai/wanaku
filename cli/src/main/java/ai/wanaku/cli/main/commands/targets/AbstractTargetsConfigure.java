package ai.wanaku.cli.main.commands.targets;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.core.services.api.TargetsService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.net.URI;
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

    protected TargetsService targetsService;

    protected void initService() {
        targetsService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(TargetsService.class);
    }
}
