package ai.wanaku.cli.main.commands.service;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
            description =
                    "Comma-separated key=value pairs (e.g., endpoint.url=http://example.com,api.key=secret). Deprecated: prefer --property or --properties-from",
            arity = "0..1")
    private String properties;

    @CommandLine.Option(
            names = {"--property"},
            description =
                    "A single key=value property; may be repeated (e.g., --property kafka.brokers=localhost:9092 --property kafka.topic=ai.requests)",
            arity = "1")
    private List<String> property = new ArrayList<>();

    @CommandLine.Option(
            names = {"--properties-from"},
            description = "Load properties from a Java .properties file",
            arity = "0..1")
    private File propertiesFrom;

    @CommandLine.Option(
            names = {"--service-name"},
            description = "Override the service name from the template",
            arity = "0..1")
    private String serviceName;

    @CommandLine.Option(
            names = {"--service-system"},
            description = "Override the system identifier from the template",
            arity = "0..1")
    private String serviceSystem;

    private Map<String, String> resolveProperties(WanakuPrinter printer) throws IOException {
        Map<String, String> propsMap = new HashMap<>();

        // 1. Legacy --properties (comma-separated), lowest precedence
        if (properties != null && !properties.isBlank()) {
            for (String pair : properties.split(",")) {
                pair = pair.trim();
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    propsMap.put(kv[0].trim(), kv[1].trim());
                } else {
                    printer.printErrorMessage(String.format("Invalid property format: '%s'%n", pair));
                    return null;
                }
            }
        }

        // 2. --properties-from <file>, overrides --properties
        if (propertiesFrom != null) {
            Properties p = new Properties();
            try (FileInputStream fis = new FileInputStream(propertiesFrom)) {
                p.load(fis);
            }
            p.forEach((k, v) -> propsMap.put((String) k, (String) v));
        }

        // 3. --property key=value (repeatable), highest precedence
        for (String pair : property) {
            pair = pair.trim();
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                propsMap.put(kv[0].trim(), kv[1].trim());
            } else {
                printer.printErrorMessage(String.format("Invalid property format: '%s'%n", pair));
                return null;
            }
        }

        return propsMap;
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        ServiceTemplateService service = initService(ServiceTemplateService.class, host);

        Map<String, String> propsMap = resolveProperties(printer);
        if (propsMap == null) {
            return EXIT_ERROR;
        }

        printer.printInfoMessage(
                String.format("Instantiating template '%s' with %d properties...%n", name, propsMap.size()));

        try {
            ServiceTemplateService.TemplateInstantiationRequest request =
                    new ServiceTemplateService.TemplateInstantiationRequest();
            request.setTemplateName(name);
            request.setProperties(propsMap);
            request.setServiceName(serviceName);
            request.setServiceSystem(serviceSystem);

            service.instantiate(request);

            printer.printSuccessMessage(
                    String.format("Service catalog created successfully from template '%s'%n", name));
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                handleNotFoundTemplate(printer, name);
                return EXIT_ERROR;
            }
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }

        return EXIT_OK;
    }

    private void handleNotFoundTemplate(WanakuPrinter printer, String templateName) {
        printer.printErrorMessage(String.format(
                "Error: template '%s' does not exist. Use 'wanaku service template list' to see available templates.%n",
                templateName));
    }
}
