package ai.wanaku.core.persistence.infinispan.discovery;

import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;
import ai.wanaku.core.persistence.infinispan.AbstractInfinispanRepository;
import jakarta.inject.Singleton;
import java.util.UUID;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;

@Singleton
public class InfinispanServiceRecordRepository extends AbstractInfinispanRepository<ActivityRecord, String> {

    protected InfinispanServiceRecordRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected Class<ActivityRecord> entityType() {
        return ActivityRecord.class;
    }

    @Override
    protected String entityName() {
        return "activityRecord";
    }

    // For testing
    void deleteAll() {
        super.deleteALl();
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }
}
