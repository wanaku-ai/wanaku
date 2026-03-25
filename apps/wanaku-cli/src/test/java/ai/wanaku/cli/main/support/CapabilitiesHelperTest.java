package ai.wanaku.cli.main.support;

import java.util.List;
import ai.wanaku.cli.main.support.CapabilitiesHelper.PrintableCapability;
import ai.wanaku.cli.main.support.CapabilitiesHelper.StatusSummary;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CapabilitiesHelperTest {

    @Test
    void testComputeStatusSummaryAllHealthy() {
        List<PrintableCapability> capabilities = List.of(
                cap("http", "tool-invoker", "healthy"),
                cap("sqs", "tool-invoker", "healthy"),
                cap("file", "resource-provider", "healthy"));

        StatusSummary summary = CapabilitiesHelper.computeStatusSummary(capabilities);

        assertEquals(3, summary.total());
        assertEquals(3, summary.healthy());
        assertEquals(0, summary.unhealthy());
        assertEquals(0, summary.down());
        assertEquals(0, summary.pending());
    }

    @Test
    void testComputeStatusSummaryMixed() {
        List<PrintableCapability> capabilities = List.of(
                cap("http", "tool-invoker", "healthy"),
                cap("sqs", "tool-invoker", "down"),
                cap("file", "resource-provider", "pending"));

        StatusSummary summary = CapabilitiesHelper.computeStatusSummary(capabilities);

        assertEquals(3, summary.total());
        assertEquals(1, summary.healthy());
        assertEquals(0, summary.unhealthy());
        assertEquals(1, summary.down());
        assertEquals(1, summary.pending());
    }

    @Test
    void testComputeStatusSummaryEmpty() {
        StatusSummary summary = CapabilitiesHelper.computeStatusSummary(List.of());

        assertEquals(0, summary.total());
        assertEquals(0, summary.healthy());
        assertEquals(0, summary.unhealthy());
        assertEquals(0, summary.down());
        assertEquals(0, summary.pending());
    }

    @Test
    void testComputeStatusSummaryAllDown() {
        List<PrintableCapability> capabilities =
                List.of(cap("http", "tool-invoker", "down"), cap("sqs", "tool-invoker", "down"));

        StatusSummary summary = CapabilitiesHelper.computeStatusSummary(capabilities);

        assertEquals(2, summary.total());
        assertEquals(0, summary.healthy());
        assertEquals(0, summary.unhealthy());
        assertEquals(2, summary.down());
        assertEquals(0, summary.pending());
    }

    @Test
    void testComputeStatusSummaryAllPending() {
        List<PrintableCapability> capabilities =
                List.of(cap("http", "tool-invoker", "-"), cap("sqs", "tool-invoker", ""));

        StatusSummary summary = CapabilitiesHelper.computeStatusSummary(capabilities);

        assertEquals(2, summary.total());
        assertEquals(0, summary.healthy());
        assertEquals(0, summary.unhealthy());
        assertEquals(0, summary.down());
        assertEquals(2, summary.pending());
    }

    @Test
    void testComputeStatusSummaryUnhealthy() {
        List<PrintableCapability> capabilities =
                List.of(cap("http", "tool-invoker", "unhealthy"), cap("sqs", "tool-invoker", "healthy"));

        StatusSummary summary = CapabilitiesHelper.computeStatusSummary(capabilities);

        assertEquals(2, summary.total());
        assertEquals(1, summary.healthy());
        assertEquals(1, summary.unhealthy());
        assertEquals(0, summary.down());
        assertEquals(0, summary.pending());
    }

    @Test
    void testDetermineServiceStatusHealthy() {
        assertEquals("healthy", CapabilitiesHelper.determineServiceStatus(activeRecord()));
    }

    @Test
    void testDetermineServiceStatusDown() {
        assertEquals("down", CapabilitiesHelper.determineServiceStatus(inactiveRecord()));
    }

    @Test
    void testDetermineServiceStatusNull() {
        assertEquals("-", CapabilitiesHelper.determineServiceStatus(null));
    }

    private static PrintableCapability cap(String service, String serviceType, String status) {
        return new PrintableCapability(service, serviceType, "localhost", 9000, status, "", List.of());
    }

    private static ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord activeRecord() {
        var record = new ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord();
        record.setHealthStatus(ai.wanaku.capabilities.sdk.api.types.discovery.HealthStatus.HEALTHY);
        return record;
    }

    private static ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord inactiveRecord() {
        var record = new ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord();
        record.setHealthStatus(ai.wanaku.capabilities.sdk.api.types.discovery.HealthStatus.DOWN);
        return record;
    }
}
