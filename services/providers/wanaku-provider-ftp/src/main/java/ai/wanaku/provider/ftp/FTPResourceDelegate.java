package ai.wanaku.provider.ftp;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.services.config.WanakuProviderConfig;
import ai.wanaku.core.services.provider.AbstractResourceDelegate;
import org.apache.camel.component.file.GenericFile;
import org.jboss.logging.Logger;

import static ai.wanaku.core.services.util.URIHelper.buildUri;

@ApplicationScoped
public class FTPResourceDelegate extends AbstractResourceDelegate {
    private static final Logger LOG = Logger.getLogger(FTPResourceDelegate.class);

    @Inject
    WanakuProviderConfig config;

    @Override
    protected String getEndpointUri(ResourceRequest request, Map<String, String> parameters) {
        return buildUri(request.getLocation(), parameters);
    }

    @Override
    protected List<String> coerceResponse(Object response) throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response instanceof GenericFile<?> genericFile) {
            Object body = genericFile.getBody();
            if (body instanceof byte[] bytes) {
                return List.of(new String(bytes));

            }

            throw new NonConvertableResponseException("The response body is not a byte array");
        }

        throw new InvalidResponseTypeException("Invalid response type from the consumer: " + response.getClass().getName());
    }

    @Override
    public Map<String, String> serviceConfigurations() {
        Map<String, String> configurations =  config.service().configurations();

        return componentOptions(config.name(), configurations);
    }
}
