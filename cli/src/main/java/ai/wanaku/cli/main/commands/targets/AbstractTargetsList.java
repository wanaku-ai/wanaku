package ai.wanaku.cli.main.commands.targets;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.core.services.api.TargetsService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import picocli.CommandLine;

import java.net.URI;

@CommandLine.Command(name = "list",
        description = "List targeted services")
@Deprecated
public abstract class AbstractTargetsList extends BaseCommand {
    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    protected TargetsService targetsService;

    protected void initService() {
        targetsService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(TargetsService.class);
    }

}
