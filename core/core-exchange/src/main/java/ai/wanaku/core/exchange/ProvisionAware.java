package ai.wanaku.core.exchange;

/**
 * Interface used by delegates that can provide access to exchange-related properties.
 * <p>
 * Implementors of this interface should supply a {@code Map} of available properties, which are identified by their respective names or keys.
 * <p>
 * These properties will often be defined in the `application.properties` file and accessed via configuration wrappers.
 */
public interface ProvisionAware {

    /**
     * Provisions a resource or tool for usage with the downstream/capability service
     *
     * @return A {@code Map} representation of valid properties keyed by name. These properties will be passed to the model as
     * acceptable arguments for the tools using this service.
     */
    ProvisionReply provision(ProvisionRequest request);
}
