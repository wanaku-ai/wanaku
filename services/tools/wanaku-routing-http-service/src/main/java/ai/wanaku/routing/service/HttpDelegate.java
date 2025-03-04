package ai.wanaku.routing.service;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.services.routing.AbstractRoutingDelegate;

@ApplicationScoped
public class HttpDelegate extends AbstractRoutingDelegate {

    @Override
    protected String coerceResponse(Object response) throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response != null) {
            return response.toString();
        }

        throw new InvalidResponseTypeException("The response is null");
    }


}
