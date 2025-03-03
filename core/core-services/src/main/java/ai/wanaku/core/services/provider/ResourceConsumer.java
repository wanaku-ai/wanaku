package ai.wanaku.core.services.provider;

/**
 * Consumes a resource from a service providing it
 */
public interface ResourceConsumer {
    /**
     * Consume the resource
     * @param uri The URI pointing to the service to consume
     * @return Whatever response was obtained by calling the server (it must be convertible to a String)
     */
    Object consume(String uri);
}
