package ai.wanaku.core.persistence.infinispan.providers;

import ai.wanaku.api.exceptions.WanakuException;
import jakarta.enterprise.inject.Produces;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.SingleFileStoreConfigurationBuilder;

public class InfinispanConfigurationProvider {
    @ConfigProperty(name = "wanaku.persistence.infinispan.base-folder", defaultValue = "${user.home}/.wanaku/router/")
    String baseFolder;

    @Produces
    Configuration newConfiguration() {
        String location = baseFolder.replace("${user.home}", System.getProperty("user.home"));
        try {
            Files.createDirectories(Paths.get(location));
        } catch (IOException e) {
            throw new WanakuException(e);
        }

        return new ConfigurationBuilder()
                .clustering()
                .cacheMode(CacheMode.LOCAL)
                .persistence()
                .passivation(false)
                .addStore(SingleFileStoreConfigurationBuilder.class)
                .location(location)
                .build();
    }
}
