package ai.wanaku.tool.exec;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.capabilities.sdk.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.capabilities.tool.AbstractToolDelegate;

@ApplicationScoped
public class ExecDelegate extends AbstractToolDelegate {

    @Override
    protected List<String> coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response == null) {
            throw new InvalidResponseTypeException("Invalid response type from the consumer: null");
        }

        // Here, convert the response from whatever format it is, to a String instance.
        return List.of(response.toString());
    }
}
