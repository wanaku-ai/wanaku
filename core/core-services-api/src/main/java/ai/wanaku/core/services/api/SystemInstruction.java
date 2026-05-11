package ai.wanaku.core.services.api;

/**
 * A deployment instruction for a single system within a service catalog.
 *
 * @param systemName the system identifier within the catalog
 * @param instruction the generated command or YAML text with placeholder markers
 * @param format the instruction format: "shell" or "yaml"
 */
public record SystemInstruction(String systemName, String instruction, String format) {}
