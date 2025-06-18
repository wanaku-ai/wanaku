package ai.wanaku.cli.main.commands.forwards;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.core.services.api.ForwardsService;
import ai.wanaku.cli.main.support.PrettyPrinter;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.net.URI;
import java.util.List;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

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

        try {
            WanakuResponse<List<ForwardReference>> wanakuResponseRestResponse = forwardsService.listForwards();
            List<ForwardReference> data = wanakuResponseRestResponse.data();
            PrettyPrinter.printForwards(data);
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
        }

    }
}
