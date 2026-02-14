package ai.wanaku.cli.main.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.wanaku.cli.main.support.CapabilitiesHelper.PrintableCapability;
import ai.wanaku.cli.main.support.CapabilitiesHelper.StatusSummary;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilitiesHelperTest {

    @Test
    void testComputeStatusSummaryAllActive() {
        List<PrintableCapability> capabilities = List.of(
                cap("http", "tool-invoker", "active"),
                cap("sqs", "tool-invoker", "active"),
                cap("file", "resource-provider", "active"));

        StatusSummary summary = CapabilitiesHelper.computeStatusSummary(capabilities);

        assertEquals(3, summary.total());
        assertEquals(3, summary.active());
        assertEquals(0, summary.inactive());
        assertEquals(0, summary.unknown());
    }

    @Test
    void testComputeStatusSummaryMixed() {
        List<PrintableCapability> capabilities = List.of(
                cap("http", "tool-invoker", "active"),
                cap("sqs", "tool-invoker", "inactive"),
                cap("file", "resource-provider", "-"));

        StatusSummary summary = CapabilitiesHelper.computeStatusSummary(capabilities);

        assertEquals(3, summary.total());
        assertEquals(1, summary.active());
        assertEquals(1, summary.inactive());
        assertEquals(1, summary.unknown());
    }

    @Test
    void testComputeStatusSummaryEmpty() {
        StatusSummary summary = CapabilitiesHelper.computeStatusSummary(List.of());

        assertEquals(0, summary.total());
        assertEquals(0, summary.active());
        assertEquals(0, summary.inactive());
        assertEquals(0, summary.unknown());
    }

    @Test
    void testComputeStatusSummaryAllInactive() {
        List<PrintableCapability> capabilities =
                List.of(cap("http", "tool-invoker", "inactive"), cap("sqs", "tool-invoker", "inactive"));

        StatusSummary summary = CapabilitiesHelper.computeStatusSummary(capabilities);

        assertEquals(2, summary.total());
        assertEquals(0, summary.active());
        assertEquals(2, summary.inactive());
        assertEquals(0, summary.unknown());
    }

    @Test
    void testComputeStatusSummaryAllUnknown() {
        List<PrintableCapability> capabilities =
                List.of(cap("http", "tool-invoker", "-"), cap("sqs", "tool-invoker", ""));

        StatusSummary summary = CapabilitiesHelper.computeStatusSummary(capabilities);

        assertEquals(2, summary.total());
        assertEquals(0, summary.active());
        assertEquals(0, summary.inactive());
        assertEquals(2, summary.unknown());
    }

    @Test
    void testDetermineServiceStatusActive() {
        assertEquals("active", CapabilitiesHelper.determineServiceStatus(activeRecord()));
    }

    @Test
    void testDetermineServiceStatusInactive() {
        assertEquals("inactive", CapabilitiesHelper.determineServiceStatus(inactiveRecord()));
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
        record.setActive(true);
        return record;
    }

    private static ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord inactiveRecord() {
        var record = new ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord();
        record.setActive(false);
        return record;
    }
}
