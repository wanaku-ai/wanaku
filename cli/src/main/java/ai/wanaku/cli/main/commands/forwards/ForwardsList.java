package ai.wanaku.cli.main.commands.forwards;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.ForwardsService;
import ai.wanaku.cli.main.support.PrettyPrinter;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.net.URI;
import java.util.List;
import picocli.CommandLine;

@CommandLine.Command(name = "list",
        description = "List forward targets")
public class ForwardsList extends BaseCommand {
    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @Override
    public void run() {
        ForwardsService forwardsService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(ForwardsService.class);

        List<ForwardReference> data = forwardsService.listForwards().getEntity().data();
        PrettyPrinter.printForwards(data);
    }
}
