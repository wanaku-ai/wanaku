package ai.wanaku.routers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import ai.wanaku.core.mcp.common.resolvers.Resolver;
import ai.wanaku.core.util.IndexHelper;

/**
 * Base provider class for tools and resources
 * @param <Y> The type of the resolver
 */
abstract class AbstractProvider<Y extends Resolver> {
    private static File createSettingsDirectory(String settingsDirectory) {
        File resourcesDir = new File(settingsDirectory);
        if (!resourcesDir.exists()) {
            resourcesDir.mkdirs();
        }

        return resourcesDir;
    }

    /**
     * Initializes the persisted index of resources
     * @param resourcesPath the path to the index file
     * @param fileName the name of the index file
     * @return the File object representing the initialized index of resources
     */
    protected File initializeResourcesIndex(String resourcesPath, String fileName) {
        File settingsDirectory = createSettingsDirectory(resourcesPath);
        File indexFile = new File(settingsDirectory, fileName);
        try {
            if (!indexFile.exists()) {
                IndexHelper.saveResourcesIndex(indexFile, Collections.EMPTY_LIST);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return indexFile;
    }

    /**
     * Initializes the persisted index of resources
     * @return the File object representing the initialized index of resources
     */
    abstract File initializeIndex();

    /**
     * Gets the resolver associated with this provider
     * @return the resolver instance
     */
    abstract Y getResolver();
}
