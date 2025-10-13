package ai.wanaku.api.types;

/**
 * A record representing a name and namespace pair for identifying Wanaku entities.
 * <p>
 * This immutable record is used throughout the Wanaku system to uniquely identify
 * capabilities, tools, and resources by combining their name with their namespace.
 * The combination of name and namespace provides a fully qualified identifier.
 * </p>
 *
 * @param name the entity name
 * @param namespace the namespace in which the entity resides
 */
public record NameNamespacePair(String name, String namespace) {}
