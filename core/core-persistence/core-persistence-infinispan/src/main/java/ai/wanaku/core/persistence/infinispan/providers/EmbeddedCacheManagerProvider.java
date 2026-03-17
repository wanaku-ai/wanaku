package ai.wanaku.core.persistence.infinispan.providers;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.util.ServiceLoader;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.protostream.SerializationContextInitializer;

@Singleton
public class EmbeddedCacheManagerProvider {
    private EmbeddedCacheManager cacheManager;

    @Produces
    @Singleton
    public EmbeddedCacheManager cacheManager() {
        if (cacheManager == null) {
            GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();
            global.nonClusteredDefault();

            var serialization = global.serialization().marshaller(new ProtoStreamMarshaller());

            ServiceLoader<SerializationContextInitializer> loader = ServiceLoader.load(
                    SerializationContextInitializer.class,
                    Thread.currentThread().getContextClassLoader());

            for (SerializationContextInitializer initializer : loader) {
                serialization.addContextInitializer(initializer);
            }

            cacheManager = new DefaultCacheManager(global.build());
        }
        return cacheManager;
    }

    @PreDestroy
    void shutdown() {
        if (cacheManager != null) {
            cacheManager.stop();
        }
    }
}
