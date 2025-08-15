package ai.wanaku.cli.main.commands.forwards;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ForwardsService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@Command(name = "add", description = "Add forward targets")
public class ForwardsAdd extends BaseCommand {
    @Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @Option(
            names = {"--name"},
            description = "The name of the service to forward",
            required = true,
            arity = "0..1")
    protected String name;

    @Option(
            names = {"--service"},
            description = "The service to forward",
            required = true,
            arity = "0..1")
    protected String service;

    @CommandLine.Option(
            names = {"-N", "--namespace"},
            description = "The namespace associated with the tool",
            defaultValue = "",
            required = true)
    private String namespace;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ForwardsService forwardsService = initService(ForwardsService.class, host);

        ForwardReference reference = new ForwardReference();
        reference.setName(name);
        reference.setAddress(service);
        reference.setNamespace(namespace);

        try (Response ignored = forwardsService.addForward(reference)) {
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
