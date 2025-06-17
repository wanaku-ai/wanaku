package ai.wanaku.provider.s3;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.ResourceNotFoundException;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.capabilities.common.ServiceOptions;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.provider.AbstractResourceDelegate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

import static ai.wanaku.core.uri.URIHelper.buildUri;

@ApplicationScoped
public class S3ResourceDelegate  extends AbstractResourceDelegate {

    @Inject
    WanakuServiceConfig config;

    @Inject
    ServiceOptions serviceOptions;

    @Override
    protected String getEndpointUri(ResourceRequest request, Map<String, String> parameters) {
        String[] locations = request.getLocation().split("/");

        if (locations.length < 2) {
            throw new IllegalArgumentException("Invalid location: " + request.getLocation() + " the location has to be in the form" +
                    " str1/str2.txt, where str1 is the bucket name and str2.txt the file on the bucket");
        }

        parameters.put("prefix", locations[locations.length - 1]);

        return "aws2-s3:" + buildUri(locations[0], parameters);
    }

    @Override
    protected List<String> coerceResponse(Object response) throws InvalidResponseTypeException, ResourceNotFoundException {
        if (response == null) {
            throw new ResourceNotFoundException("File not found");
        }

        if (response instanceof byte[] bytes) {
            return List.of(new String(bytes));
        }

        throw new InvalidResponseTypeException("Invalid response type from the consumer: " + response.getClass().getName());
    }

    @Override
    public Map<String, String> serviceConfigurations() {
        Map<String, String> configurations =  config.service().configurations();

        return serviceOptions.merge(config.name(), configurations);
    }
}
