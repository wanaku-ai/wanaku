package ai.wanaku.cli.main.commands.service;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ServiceTemplateService;
import picocli.CommandLine;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

@CommandLine.Command(name = "instantiate", description = "Instantiate a service template into a service catalog")
public class ServiceTemplateInstantiate extends BaseCommand {

    @CommandLine.Option(
            names = {"--name"},
            description = "The template name",
            required = true)
    private String name;

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"--properties"},
            description = "Comma-separated key=value pairs (e.g., endpoint.url=http://example.com,api.key=secret)",
            arity = "0..1")
    private String properties;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ServiceTemplateService service = initService(ServiceTemplateService.class, host);

        // Parse properties
        Map<String, String> propsMap = new HashMap<>();
        if (properties != null && !properties.isBlank()) {
            String[] pairs = properties.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    propsMap.put(kv[0].trim(), kv[1].trim());
                } else {
                    printer.printErrorMessage(String.format("Invalid property format: '%s'%n", pair));
                    return EXIT_ERROR;
                }
            }
        }

        printer.printInfoMessage(
                String.format("Instantiating template '%s' with %d properties...%n", name, propsMap.size()));

        try {
            ServiceTemplateService.TemplateInstantiationRequest request =
                    new ServiceTemplateService.TemplateInstantiationRequest();
            request.setTemplateName(name);
            request.setProperties(propsMap);

            service.instantiate(request);

            printer.printSuccessMessage(
                    String.format("Service catalog created successfully from template '%s'%n", name));
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }

        return EXIT_OK;
    }
}
