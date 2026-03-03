package ai.wanaku.core.persistence.infinispan.providers;

import jakarta.enterprise.inject.Produces;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.StorageType;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

public class InfinispanConfigurationProvider {
    @ConfigProperty(name = "wanaku.persistence.infinispan.base-folder", defaultValue = "${user.home}/.wanaku/router/")
    String baseFolder;

    @ConfigProperty(name = "wanaku.persistence.infinispan.max-entries", defaultValue = "10000")
    int maxEntries;

    @ConfigProperty(name = "wanaku.persistence.infinispan.file-store", defaultValue = "true")
    boolean fileStore;

    @Produces
    Configuration newConfiguration() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.clustering()
                .cacheMode(CacheMode.LOCAL)
                .memory()
                .storage(StorageType.HEAP)
                .maxCount(maxEntries);

        if (fileStore) {
            String location = resolveBaseFolder();
            try {
                Files.createDirectories(Paths.get(location));
            } catch (IOException e) {
                throw new WanakuException(e);
            }

            builder.persistence()
                    .passivation(false)
                    .addSoftIndexFileStore()
                    .dataLocation(location)
                    .indexLocation(location)
                    .shared(false)
                    .preload(true)
                    .purgeOnStartup(false);
        }

        return builder.build();
    }

    private String resolveBaseFolder() {
        return baseFolder.replace("${user.home}", System.getProperty("user.home"));
    }
}
