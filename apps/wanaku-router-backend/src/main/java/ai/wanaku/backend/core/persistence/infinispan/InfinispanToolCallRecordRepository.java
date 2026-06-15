package ai.wanaku.backend.core.persistence.infinispan;

import java.util.List;
import java.util.UUID;
import org.infinispan.commons.api.query.Query;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import ai.wanaku.backend.common.ToolCallRecord;
import ai.wanaku.backend.core.persistence.api.ToolCallRecordRepository;

public class InfinispanToolCallRecordRepository extends AbstractInfinispanRepository<ToolCallRecord, String>
        implements ToolCallRecordRepository {

    public InfinispanToolCallRecordRepository(EmbeddedCacheManager cacheManager, Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected String entityName() {
        return "tool-call-record";
    }

    @Override
    protected Class<ToolCallRecord> entityType() {
        return ToolCallRecord.class;
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public List<ToolCallRecord> findByToolName(String toolName) {
        Query<ToolCallRecord> query = cacheManager
                .getCache(entityName())
                .query("from ai.wanaku.backend.common.ToolCallRecord r where r.toolName = :toolName");
        query.setParameter("toolName", toolName);
        return query.execute().list();
    }

    @Override
    public List<ToolCallRecord> findByConnectionId(String connectionId) {
        Query<ToolCallRecord> query = cacheManager
                .getCache(entityName())
                .query("from ai.wanaku.backend.common.ToolCallRecord r where r.connectionId = :connectionId");
        query.setParameter("connectionId", connectionId);
        return query.execute().list();
    }

    @Override
    public int deleteOlderThan(long timestampMillis) {
        Query<Object[]> query = cacheManager
                .getCache(entityName())
                .query("DELETE FROM ai.wanaku.backend.common.ToolCallRecord r WHERE r.timestamp < :cutoff");
        query.setParameter("cutoff", timestampMillis);
        return query.executeStatement();
    }
}
