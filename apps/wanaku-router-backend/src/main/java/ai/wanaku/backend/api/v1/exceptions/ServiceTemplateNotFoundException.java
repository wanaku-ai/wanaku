package ai.wanaku.backend.api.v1.exceptions;

import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

/**
 * Exception thrown when a requested service template does not exist.
 */
public class ServiceTemplateNotFoundException extends WanakuException {
    public ServiceTemplateNotFoundException(String message) {
        super(message);
    }
}
