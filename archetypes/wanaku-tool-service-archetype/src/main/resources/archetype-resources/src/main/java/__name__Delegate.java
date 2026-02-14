package $

import java.util.List;

import ai.wanaku.capabilities.sdk.api.exceptions.InvalidResponseTypeException;
import jakarta.enterprise.context.ApplicationScoped;


@ApplicationScoped
public class ${name}Delegate extends AbstractToolDelegate {

    @Override
    protected List<String> coerceResponse(Object response) throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response == null) {
            throw new InvalidResponseTypeException("Invalid response type from the consumer: null");
        }

        // Here, convert the response from whatever format it is, to a String instance.
        throw new InvalidResponseTypeException("The downstream service has not implemented the response coercion method");
    }
}
