package ai.wanaku.backend.core.persistence.api;

import java.util.List;
import ai.wanaku.backend.common.ToolCallRecord;

public interface ToolCallRecordRepository extends WanakuRepository<ToolCallRecord, String> {

    List<ToolCallRecord> findByToolName(String toolName);

    List<ToolCallRecord> findByConnectionId(String connectionId);

    int deleteOlderThan(long timestampMillis);
}
