package org.wanaku.routers.camel;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.wanaku.api.resolvers.Resolver;
import org.wanaku.core.util.IndexHelper;
import org.wanaku.routers.camel.proxies.Proxy;

abstract class AbstractProvider<T extends Proxy, Y extends Resolver> {
    private static File createSettingsDirectory(String settingsDirectory) {
        File resourcesDir = new File(settingsDirectory);
        if (!resourcesDir.exists()) {
            resourcesDir.mkdirs();
        }

        return resourcesDir;
    }

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

    protected void initializeCamel(CamelContext camelContext) {
        if (!camelContext.isStarted()) {
            camelContext.start();
        }
    }

    abstract File initializeIndex();

    abstract Map<String, T> loadProxies();

    abstract Y getResolver();
}
