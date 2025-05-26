package ai.wanaku.core.persistence.infinispan;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.persistence.api.ForwardReferenceRepository;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import io.quarkus.arc.lookup.LookupIfProperty;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.SingleFileStoreConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;

public class InfinispanPersistenceConfiguration {

    @ConfigProperty(name = "wanaku.persistence.infinispan.base-folder", defaultValue = "${user.home}/.wanaku/router/")
    String baseFolder;

    @Inject
    EmbeddedCacheManager cacheManager;

    @Produces
    @LookupIfProperty(name = "wanaku.persistence", stringValue = "infinispan")
    ResourceReferenceRepository resourceReferenceRepository() {
        return new InfinispanResourceReferenceRepository(cacheManager, newConfiguration());
    }

    @Produces
    @LookupIfProperty(name = "wanaku.persistence", stringValue = "infinispan")
    ToolReferenceRepository toolReferenceRepository() {
        return new InfinispanToolReferenceRepository(cacheManager, newConfiguration());
    }

    @Produces
    @LookupIfProperty(name = "wanaku.persistence", stringValue = "infinispan")
    ForwardReferenceRepository forwardReferenceRepository() {
        return new InfinispanForwardReferenceRepository(cacheManager, newConfiguration());
    }

    // Currently enabled only for development purposes
    @Produces
    @LookupIfProperty(name = "wanaku.persistence", stringValue = "infinispan-dev")
    InfinispanToolTargetRepository toolTargetRepository() {
        return new InfinispanToolTargetRepository(cacheManager, newConfiguration());
    }

    @Produces
    @LookupIfProperty(name = "wanaku.persistence", stringValue = "infinispan-dev")
    InfinispanResourceTargetRepository resourceTargetRepository() {
        return new InfinispanResourceTargetRepository(cacheManager, newConfiguration());
    }

    @Produces
    @LookupIfProperty(name = "wanaku.persistence", stringValue = "infinispan-dev")
    ServiceRegistry serviceRegistry() {
        return new InfinispanServiceRegistry();
    }

    private Configuration newConfiguration() {
        String location = baseFolder.replace("${user.home}", System.getProperty("user.home"));

        return new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.LOCAL)
                .persistence()
                .passivation(false)
                .addStore(SingleFileStoreConfigurationBuilder.class)
                .location(location)
                .build();
    }

//    @IfBuildProfile("prod")

}
