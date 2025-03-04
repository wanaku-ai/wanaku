package ai.wanaku.routing.yaml.route.service;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.core.services.routing.AbstractRoutingDelegate;

@ApplicationScoped
public class YamlRouteDelegate extends AbstractRoutingDelegate {

    protected String coerceResponse(Object response) throws InvalidResponseTypeException {
        if (response != null) {
            return response.toString();
        }

        throw new InvalidResponseTypeException("The response is null");
    }

}
