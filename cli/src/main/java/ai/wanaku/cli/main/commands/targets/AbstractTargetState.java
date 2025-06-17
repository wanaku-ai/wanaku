package ai.wanaku.cli.main.commands.targets;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.core.services.api.TargetsService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.net.URI;
import picocli.CommandLine;

@CommandLine.Command(name = "state",
        description = "Get the state of the targeted services")
public abstract class AbstractTargetState extends BaseCommand {
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
