package ai.wanaku.provider.ftp;

import static ai.wanaku.core.uri.URIHelper.buildUri;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.capabilities.common.ServiceOptions;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.provider.AbstractResourceDelegate;
import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.runtime.camel.CamelQueryParameterBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.apache.camel.component.file.GenericFile;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FTPResourceDelegate extends AbstractResourceDelegate {
    private static final Logger LOG = Logger.getLogger(FTPResourceDelegate.class);

    @Inject
    ServiceOptions serviceOptions;

    @Inject
    WanakuServiceConfig config;

    @Override
    protected String getEndpointUri(ResourceRequest request, ConfigResource configResource) {
        Map<String, String> parameters = CamelQueryParameterBuilder.build(configResource);
        return buildUri(request.getLocation(), parameters);
    }

    @Override
    protected List<String> coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response instanceof GenericFile<?> genericFile) {
            Object body = genericFile.getBody();
            if (body instanceof byte[] bytes) {
                return List.of(new String(bytes));
            }

            throw new NonConvertableResponseException("The response body is not a byte array");
        }

        throw new InvalidResponseTypeException("Invalid response type from the consumer: "
                + response.getClass().getName());
    }
}
