package ai.wanaku.core.capabilities.common;

import java.util.Map;

/**
 * Defines the contract for merging static and runtime service configuration options.
 * <p>
 * This interface is implemented by components that need to combine configuration options
 * from different sources, such as configuration files and runtime parameters. The merge
 * operation allows dynamic configuration to override or supplement static configuration
 * defined at deployment time.
 * <p>
 * Implementations should ensure that the provided map is modifiable, as options will be
 * merged directly into it. The merge strategy (e.g., which source takes precedence) is
 * determined by the implementing class.
 */
public interface ServiceOptions {

    /**
     * Merge runtime configurations defined in the configurations file with other configurations defined in runtime. Options will
     * be merged into the provided map (i.e.: must not be an unmodifiable map).
     * @param serviceName the name of the service
     * @param staticOpt the static options map
     * @return The merged map with the static and runtime options merged
     */
    Map<String, String> merge(String serviceName, Map<String, String> staticOpt);
}
