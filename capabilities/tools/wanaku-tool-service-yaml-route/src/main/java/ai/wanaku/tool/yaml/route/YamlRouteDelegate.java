package ai.wanaku.tool.yaml.route;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.core.capabilities.tool.AbstractToolDelegate;
import java.util.List;

@ApplicationScoped
public class YamlRouteDelegate extends AbstractToolDelegate {

    protected List<String> coerceResponse(Object response) throws InvalidResponseTypeException {
        if (response != null) {
            return List.of(response.toString());
        }

        throw new InvalidResponseTypeException("The response is null");
    }

}
