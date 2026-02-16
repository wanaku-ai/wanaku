package ai.wanaku.core.services.api;

/**
 * Represents the aggregated health status of the entire capability fleet.
 *
 * @param total total number of registered capabilities
 * @param healthy number of healthy capabilities
 * @param warning number of capabilities in a warning state (unhealthy or pending)
 * @param down number of capabilities that are down
 * @param overall the overall fleet status: "Healthy", "Warning", or "Down"
 */
public record FleetStatus(int total, int healthy, int warning, int down, String overall) {}
