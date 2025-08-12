package ai.wanaku.provider.ftp.file;

import static ai.wanaku.core.uri.URIHelper.buildUri;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.provider.AbstractResourceDelegate;
import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.runtime.camel.CamelQueryParameterBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.camel.component.file.GenericFile;

@ApplicationScoped
public class FileResourceDelegate extends AbstractResourceDelegate {
    @Inject
    WanakuServiceConfig config;

    @Override
    protected String getEndpointUri(ResourceRequest request, ConfigResource configResource) {
        final Map<String, String> parameters = config.service().defaults();

        Map<String, String> toolsParameters = CamelQueryParameterBuilder.build(configResource);
        parameters.putAll(toolsParameters);

        File file = new File(request.getLocation());
        String path;
        if (file.isDirectory()) {
            path = file.getAbsolutePath();
            parameters.putIfAbsent("recursive", "true");
        } else {
            path = file.getParent();
            parameters.putIfAbsent("fileName", file.getName());
        }

        return buildUri(config.baseUri(), path, parameters);
    }

    @Override
    protected List<String> coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response instanceof GenericFile<?> genericFile) {
            String fileName = genericFile.getAbsoluteFilePath();

            try {
                Path path = Path.of(fileName);
                if (Files.exists(path)) {
                    return List.of(Files.readString(path));
                } else {
                    throw new NonConvertableResponseException("The file does not exist: " + fileName);
                }

            } catch (IOException e) {
                throw new NonConvertableResponseException(e);
            }
        }

        if (response == null) {
            throw new InvalidResponseTypeException("Unable the read the file: no response (does the file exist?)");
        }

        throw new InvalidResponseTypeException("Invalid response type from the consumer: "
                + response.getClass().getName());
    }
}
