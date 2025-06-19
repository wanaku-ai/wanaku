package ${package};

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.provider.AbstractResourceDelegate;
import ai.wanaku.core.config.provider.api.ConfigResource;
import org.jboss.logging.Logger;

#if ( $wanaku-capability-type == "camel")
import ai.wanaku.core.runtime.camel.CamelQueryParameterBuilder;
#end

import static ai.wanaku.core.uri.URIHelper.buildUri;

@ApplicationScoped
public class ${name}ResourceDelegate extends AbstractResourceDelegate {
    private static final Logger LOG = Logger.getLogger(${name}ResourceDelegate.class);

    @Inject
    WanakuServiceConfig config;

    @Override
    protected String getEndpointUri(ResourceRequest request, ConfigResource configResource) {
        /*
         * Here you build the URI based on the request parameters.
         * The parameters are already merged w/ the requested ones, but
         * feel free to override if necessary.
         *
         * For instance, suppose the component has an option "fileName" and
         * you need to set it, then use:
         *
         * parameters.putIfAbsent("fileName", file.getName());
         *
         * After the map has been adjusted, just call the buildUri from URIHelper
         */
#if ( $wanaku-capability-type == "camel")
        Map<String, String> parameters = CamelQueryParameterBuilder.build(configResource);
        return buildUri(request.getLocation(), parameters);
#else
        return buildUri(request.getLocation(), Map.of());
#end
    }

    @Override
    protected List<String> coerceResponse(Object response) throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response == null) {
            throw new InvalidResponseTypeException("Invalid response type from the consumer: null");
        }

        // Here, convert the response from whatever format it is, to a String instance.
        throw new InvalidResponseTypeException("The downstream service has not implemented the response coercion method");
    }
}
