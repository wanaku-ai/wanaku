package ai.wanaku.backend.core.persistence.api;

import java.util.List;
import ai.wanaku.backend.common.ToolCallRecord;

/**
 * Repository for persisted tool call audit records.
 * <p>
 * Stores non-sensitive metadata about tool invocations for historical queries
 * and debugging. Records are automatically created from CDI events fired by
 * {@code EventNotifier} and cleaned up by a scheduled retention job.
 */
public interface ToolCallRecordRepository extends WanakuRepository<ToolCallRecord, String> {

    /**
     * Finds all records for the given tool name.
     *
     * @param toolName the tool name to search for
     * @return list of matching records, or an empty list if none found
     */
    List<ToolCallRecord> findByToolName(String toolName);

    /**
     * Finds all records for the given connection ID.
     *
     * @param connectionId the MCP connection ID to search for
     * @return list of matching records, or an empty list if none found
     */
    List<ToolCallRecord> findByConnectionId(String connectionId);

    /**
     * Deletes all records with a timestamp older than the given cutoff.
     *
     * @param timestampMillis the epoch-millis cutoff; records older than this are deleted
     * @return the number of records deleted
     */
    int deleteOlderThan(long timestampMillis);
}
