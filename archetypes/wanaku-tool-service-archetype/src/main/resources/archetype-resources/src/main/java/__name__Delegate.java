package ${package};

import java.util.List;

import ai.wanaku.capabilities.sdk.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.capabilities.sdk.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.capabilities.tool.AbstractToolDelegate;
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class ${name}Delegate extends AbstractToolDelegate {

    @Override
    protected List<String> coerceResponse(Object response) throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response == null) {
            throw new InvalidResponseTypeException("Invalid response type from the consumer: null");
        }

        return List.of(response.toString());
    }
}
