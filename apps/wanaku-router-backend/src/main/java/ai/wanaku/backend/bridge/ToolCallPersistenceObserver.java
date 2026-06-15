package ai.wanaku.backend.bridge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import io.quarkus.scheduler.Scheduled;
import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.backend.common.ToolCallRecord;
import ai.wanaku.backend.core.persistence.api.ToolCallRecordRepository;

@ApplicationScoped
public class ToolCallPersistenceObserver {
    private static final Logger LOG = Logger.getLogger(ToolCallPersistenceObserver.class);

    @Inject
    ToolCallRecordRepository repository;

    @ConfigProperty(name = "wanaku.tool-call-logging.retention-days", defaultValue = "7")
    int retentionDays;

    void onToolCallEvent(@ObservesAsync ToolCallEvent event) {
        try {
            ToolCallRecord record = ToolCallRecord.fromEvent(event);
            repository.persist(record);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to persist tool call event %s", event.getEventId());
        }
    }

    @Scheduled(every = "{wanaku.tool-call-logging.cleanup-interval:24h}")
    void cleanupExpiredRecords() {
        try {
            long cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS).toEpochMilli();
            int deleted = repository.deleteOlderThan(cutoff);
            if (deleted > 0) {
                LOG.infof("Cleaned up %d expired tool call records (older than %d days)", deleted, retentionDays);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to clean up expired tool call records");
        }
    }
}
