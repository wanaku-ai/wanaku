package ai.wanaku.backend.api.v1.common;

import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.io.ProvisionAwarePayload;

/**
 * Shared validation logic for {@link ProvisionAwarePayload} objects.
 * <p>
 * This utility extracts the null-checking boilerplate that was previously duplicated
 * across {@code ToolsResource}, {@code ResourcesResource}, and {@code PromptsResource}.
 * Type-specific validation (e.g. checking a tool or resource name) remains in the
 * respective resource classes.
 */
public final class PayloadValidator {

    private PayloadValidator() {
        // utility class
    }

    /**
     * Validates that the given provision-aware payload and its nested payload are non-null.
     *
     * @param resource the payload to validate
     * @throws WanakuException if {@code resource} is null or its {@link ProvisionAwarePayload#getPayload()} is null
     */
    public static void validate(ProvisionAwarePayload<?> resource) throws WanakuException {
        if (resource == null) {
            throw new WanakuException("The request itself must not be null");
        }
        if (resource.getPayload() == null) {
            throw new WanakuException("The 'payload' is required for this request");
        }
    }
}
