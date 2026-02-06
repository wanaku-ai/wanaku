package ai.wanaku.core.mcp.providers;

import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

/**
 * Represents a capability that has become stale, combining the service target
 * information with its activity record for staleness assessment.
 *
 * @param serviceTarget the service target representing the capability
 * @param activityRecord the activity record containing last seen time and active status
 */
public record StaleCapability(ServiceTarget serviceTarget, ActivityRecord activityRecord) {}
