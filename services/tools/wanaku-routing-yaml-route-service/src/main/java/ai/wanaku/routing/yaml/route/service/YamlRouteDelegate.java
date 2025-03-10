package ai.wanaku.routing.yaml.route.service;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.core.services.routing.AbstractRoutingDelegate;
import java.util.List;

@ApplicationScoped
public class YamlRouteDelegate extends AbstractRoutingDelegate {

    protected List<String> coerceResponse(Object response) throws InvalidResponseTypeException {
        if (response != null) {
            return List.of(response.toString());
        }

        throw new InvalidResponseTypeException("The response is null");
    }

}
