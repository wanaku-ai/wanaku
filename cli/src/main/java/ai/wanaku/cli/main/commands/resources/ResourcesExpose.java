package ai.wanaku.cli.main.commands.resources;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.io.ResourcePayload;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.FileHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ResourcesService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(name = "expose", description = "Expose resources")
public class ResourcesExpose extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(
            names = {"--location"},
            description = "The of the resource",
            required = true,
            arity = "0..1")
    private String location;

    @CommandLine.Option(
            names = {"--type"},
            description = "The type of the resource",
            required = true,
            arity = "0..1")
    private String type;

    @CommandLine.Option(
            names = {"--name"},
            description = "A human-readable name for the resource",
            required = true,
            arity = "0..1")
    private String name;

    @CommandLine.Option(
            names = {"-N", "--namespace"},
            description = "The namespace associated with the tool",
            defaultValue = "",
            required = true)
    private String namespace;

    @CommandLine.Option(
            names = {"--description"},
            description = "A brief description of the resource",
            required = true,
            arity = "0..1")
    private String description;

    @CommandLine.Option(
            names = {"--mimeType"},
            description = "The MIME type of the resource (i.e.: text/plain)",
            required = true,
            defaultValue = "text/plain",
            arity = "0..1")
    private String mimeType;

    @CommandLine.Option(
            names = {"--param"},
            description = "One or more parameters for the resource",
            arity = "0..n")
    private List<String> params;

    @CommandLine.Option(
            names = {"--configuration-from-file"},
            description = "Configure the capability provider using the given file",
            arity = "0..1")
    private String configurationFromFile;

    @CommandLine.Option(
            names = {"--secrets-from-file"},
            description = "Add the given secrets to the capability provider using the given file",
            arity = "0..1")
    private String secretsFromFile;

    ResourcesService resourcesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        resourcesService = initService(ResourcesService.class, host);
        ResourceReference resource = new ResourceReference();
        resource.setLocation(location);
        resource.setType(type);
        resource.setName(name);
        resource.setDescription(description);
        resource.setMimeType(mimeType);
        resource.setNamespace(namespace);

        ResourcePayload resourcePayload = new ResourcePayload();
        resourcePayload.setPayload(resource);

        FileHelper.loadConfigurationSources(configurationFromFile, resourcePayload::setConfigurationData);
        FileHelper.loadConfigurationSources(secretsFromFile, resourcePayload::setSecretsData);

        if (params != null) {
            List<ResourceReference.Param> paramsList = new ArrayList<>();
            for (String paramStr : params) {
                ResourceReference.Param param = new ResourceReference.Param();

                String[] split = paramStr.split("=");
                param.setName(split[0]);

                if (split.length > 1) {
                    param.setValue(split[1]);
                }
                paramsList.add(param);
            }
            resource.setParams(paramsList);
        }

        try (Response response = resourcesService.exposeWithPayload(resourcePayload)) {

        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
        return EXIT_OK;
    }
}
