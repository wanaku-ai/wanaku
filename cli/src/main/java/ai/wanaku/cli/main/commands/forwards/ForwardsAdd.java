package ai.wanaku.cli.main.commands.forwards;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.ForwardsService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.net.URI;
import picocli.CommandLine;

@CommandLine.Command(name = "add",
        description = "Add forward targets")
public class ForwardsAdd extends BaseCommand {
    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(names = {"--name"}, description = "The name of the service to forward", required = true, arity = "0..1")
    protected String name;

    @CommandLine.Option(names = {"--service"}, description = "The service to forward", required = true, arity = "0..1")
    protected String service;

    @Override
    public void run() {
        ForwardsService forwardsService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(ForwardsService.class);

        ForwardReference reference = new ForwardReference();
        reference.setName(name);
        reference.setAddress(service);

        forwardsService.addForward(reference);
    }
}
