package ai.wanaku.core.config.provider.api;


/**
 * An interface for writing sensitive secret data.
 * This interface extends {@link ConfigWriter}, meaning that any implementation
 * of {@code SecretWriter} must also implement the {@code write} method
 * defined in {@code ConfigWriter}. However, {@code SecretWriter} specifically
 * denotes that the data being written is considered sensitive (a "secret")
 * and thus implementations should ensure appropriate security measures
 * (e.g., encryption, restricted access) when handling and storing this data.
 *
 * <p>While the method signature {@code write(String id, String data)} is inherited,
 * the semantic implication is that {@code data} contains confidential information.
 */
public interface SecretWriter extends ConfigWriter {
}
