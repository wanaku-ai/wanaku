package ai.wanaku.tool.telegram;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.capabilities.tool.AbstractToolDelegate;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class TelegramDelegate extends AbstractToolDelegate {

    @Override
    protected List<String> coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response != null) {
            return List.of(response.toString());
        }

        // Here, convert the response from whatever format it is, to a String instance.
        throw new InvalidResponseTypeException(
                "The downstream service has not implemented the response coercion method");
    }
}
