package ai.wanaku.core.services.api;

import java.time.Instant;

/**
 * Data transfer object representing information about a stale capability.
 * Used for API responses when listing or cleaning up stale capabilities.
 *
 * @param id the unique identifier of the capability
 * @param serviceName the name of the service
 * @param serviceType the type of service (e.g., "tool-invoker", "resource-provider")
 * @param host the host where the service is running
 * @param port the port on which the service is listening
 * @param active whether the service is marked as active
 * @param lastSeen the timestamp when the service was last seen (may be null if never pinged)
 */
public record StaleCapabilityInfo(
        String id, String serviceName, String serviceType, String host, int port, boolean active, Instant lastSeen) {}
