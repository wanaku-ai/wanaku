package ai.wanaku.core.persistence.infinispan.providers;

import jakarta.enterprise.inject.Produces;

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

        return new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.LOCAL)
                .persistence()
                .passivation(false)
                .addStore(SingleFileStoreConfigurationBuilder.class)
                .location(location)
                .build();
    }
}
