package ai.wanaku.provider.ftp.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.services.config.WanakuProviderConfig;
import ai.wanaku.core.services.provider.AbstractResourceDelegate;
import org.apache.camel.component.file.GenericFile;

import static ai.wanaku.core.services.util.URIHelper.buildUri;

@ApplicationScoped
public class FileResourceDelegate extends AbstractResourceDelegate {
    @Inject
    WanakuProviderConfig config;

    @Override
    protected String getEndpointUri(ResourceRequest request, Map<String, String> parameters) {
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
    protected String coerceResponse(Object response) throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response instanceof GenericFile<?> genericFile) {
            String fileName = genericFile.getAbsoluteFilePath();

            try {
                Path path = Path.of(fileName);
                if (Files.exists(path)) {
                    return Files.readString(path);
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

        throw new InvalidResponseTypeException("Invalid response type from the consumer: " + response.getClass().getName());
    }
}
