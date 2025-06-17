package ai.wanaku.cli.main.commands.tools;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import ai.wanaku.core.services.api.ToolsService;
import java.net.URI;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import org.jboss.logging.Logger;
import ai.wanaku.cli.main.commands.BaseCommand;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

@CommandLine.Command(name = "remove",description = "Remove tools")
public class ToolsRemove extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolsRemove.class);

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(names = {"-n", "--name"}, description="Name of the tool to remove", required = true)
    private String name;

    ToolsService toolsService;

    @Override
    public void run() {
        toolsService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(ToolsService.class);

        try (Response ignored = toolsService.remove(name)) {

        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                System.err.printf("Tool not found (%s): %s%n",
                        name, response.getStatusInfo().getReasonPhrase());

                System.exit(1);
            } else {
                commonResponseErrorHandler(response);
            }
        }

    }

}
