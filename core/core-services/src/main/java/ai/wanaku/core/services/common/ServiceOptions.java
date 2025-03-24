package ai.wanaku.core.services.common;

import java.util.Map;

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
