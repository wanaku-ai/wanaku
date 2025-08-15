package ai.wanaku.core.config.provider.api;

import java.net.URI;

/**
 * Represents the result of a provisioning operation, encapsulating the URIs
 * where the configurations and secrets have been provisioned.
 * This record provides an immutable way to hold the locations of both
 * configuration data and sensitive secret data after they have been
 * successfully provisioned by a {@link ConfigProvisioner}.
 *
 * @param configurationsUri The {@link URI} pointing to the location of the provisioned configurations.
 * This may be {@code null} if no configurations were provisioned or if
 * the operation did not yield a specific URI for configurations.
 * @param secretsUri The {@link URI} pointing to the location of the provisioned secrets.
 * This may be {@code null} if no secrets were provisioned or if
 * the operation did not yield a specific URI for secrets.
 */
public record ProvisionedConfig(URI configurationsUri, URI secretsUri) {}
