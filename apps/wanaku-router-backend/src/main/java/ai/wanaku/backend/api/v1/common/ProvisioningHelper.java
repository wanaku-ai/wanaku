package ai.wanaku.backend.api.v1.common;

import java.util.function.BiConsumer;
import ai.wanaku.backend.bridge.ProvisionerBridge;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.types.io.ProvisionAwarePayload;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

/**
 * Shared provisioning logic extracted from {@code ToolsBean} and {@code ResourcesBean}.
 * <p>
 * Both beans follow the same four-step pattern when provisioning a capability:
 * <ol>
 *   <li>Resolve the target service via {@link ProvisionerBridge#resolveService}</li>
 *   <li>Call {@link ProvisionerBridge#provision} with the configuration and secrets data</li>
 *   <li>Set the configuration URI on the reference</li>
 *   <li>Set the secrets URI on the reference</li>
 * </ol>
 * This helper encapsulates those steps so callers only supply the type-specific parts
 * (reference name, type, service-type value, and URI setters).
 */
public final class ProvisioningHelper {

    private ProvisioningHelper() {
        // utility class
    }

    /**
     * Provisions a capability and applies the resulting URIs to the reference.
     *
     * @param provisionerBridge the bridge used for service resolution and provisioning
     * @param payload           the provision-aware payload carrying configuration and secrets data
     * @param referenceName     the name of the reference being provisioned (used as a key)
     * @param referenceType     the type of the reference (e.g. "http", "camel")
     * @param serviceTypeValue  the service-type value (e.g. {@code ServiceType.TOOL_INVOKER.asValue()})
     * @param uriSetter         a callback that receives the configuration URI and secrets URI strings;
     *                          the first argument is the configuration URI, the second is the secrets URI
     * @return the {@link ProvisioningReference} produced by the bridge, in case the caller needs
     *         access to additional data such as the property map
     */
    public static ProvisioningReference provision(
            ProvisionerBridge provisionerBridge,
            ProvisionAwarePayload<?> payload,
            String referenceName,
            String referenceType,
            String serviceTypeValue,
            BiConsumer<String, String> uriSetter) {

        ServiceTarget service = provisionerBridge.resolveService(referenceType, serviceTypeValue);

        ProvisioningReference provisioningReference = provisionerBridge.provision(
                referenceName, payload.getConfigurationData(), payload.getSecretsData(), service);

        String configURI = provisioningReference.configurationURI() != null
                ? provisioningReference.configurationURI().toString()
                : "";
        String secretsURI = provisioningReference.secretsURI() != null
                ? provisioningReference.secretsURI().toString()
                : "";
        uriSetter.accept(configURI, secretsURI);

        return provisioningReference;
    }
}
