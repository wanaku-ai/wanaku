package ai.wanaku.core.exchange;

/**
 * A delegate that can be used by services that provide resources
 */
public interface ResourceAcquirerDelegate extends ConfigurableDelegate, RegisterAware {

    /**
     * Acquire the resource and provide it to the requester
     * @param request the resource request
     * @return the resource data and associated metadata about the request
     */
    ResourceReply acquire(ResourceRequest request);
}
