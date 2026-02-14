package ai.wanaku.tool.tavily;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.stream.Collectors;
import ai.wanaku.capabilities.sdk.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.capabilities.sdk.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.capabilities.tool.AbstractToolDelegate;

@ApplicationScoped
public class TavilyDelegate extends AbstractToolDelegate {

    @SuppressWarnings("unchecked")
    @Override
    protected List<String> coerceResponse(Object response)
            throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response == null) {
            throw new InvalidResponseTypeException("Invalid response type from the consumer: null");
        }

        // Here, convert the response from whatever format it is, to a String instance.
        if (response instanceof String) {
            return List.of(response.toString());
        }

        if (response instanceof List) {
            List<String> responseStrings = (List<String>) response;
            // Annoyingly, the component sometimes return null elements. We have to filter them
            return responseStrings.stream()
                    .filter(s -> s != null && !s.isEmpty())
                    .collect(Collectors.toList());
        }

        throw new InvalidResponseTypeException("Invalid response type from the consumer: "
                + response.getClass().getName());
    }
}
