package ai.wanaku.cli.main.commands.targets.tools;

import ai.wanaku.cli.main.commands.targets.AbstractTargets;
import ai.wanaku.cli.main.support.WanakuPrinter;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

@Deprecated
@Command(name = "configure", description = "Configure services")
public class ToolsConfigure extends AbstractTargets {

    @Option(names = { "--service" }, description = "The service to link", required = true, arity = "0..1")
    protected String service;

    @Option(names = { "--option" }, description = "The option to set", required = true, arity = "0..1")
    protected String option;

    @Option(names = { "--value" }, description = "The value to set the option", required = true, arity = "0..1")
    protected String value;


    @Override
    protected Integer doTargetCall(WanakuPrinter printer) throws IOException {
        try (Response ignored = targetsService.toolsConfigure(service, option, value)) {
        } catch (WebApplicationException e) {
            Response response = e.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                System.out.println("There is no configuration or service with that name");
                return EXIT_ERROR;
            } else {
                commonResponseErrorHandler(response);
                return EXIT_ERROR;
            }
        }
        return EXIT_OK;
    }
}
