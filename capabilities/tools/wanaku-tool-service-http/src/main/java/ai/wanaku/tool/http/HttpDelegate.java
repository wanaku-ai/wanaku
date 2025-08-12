package ai.wanaku.tool.http;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.capabilities.tool.AbstractToolDelegate;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class HttpDelegate extends AbstractToolDelegate {

    @Override
    protected List<String> coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response != null) {
            return List.of(response.toString());
        }

        throw new InvalidResponseTypeException("The response is null");
    }
}
