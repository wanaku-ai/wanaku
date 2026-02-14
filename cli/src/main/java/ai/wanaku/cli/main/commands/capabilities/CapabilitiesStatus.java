package ai.wanaku.cli.main.commands.capabilities;

import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.CapabilitiesHelper;
import ai.wanaku.cli.main.support.CapabilitiesHelper.PrintableCapability;
import ai.wanaku.cli.main.support.CapabilitiesHelper.StatusSummary;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.CapabilitiesService;

import static ai.wanaku.cli.main.support.CapabilitiesHelper.ACTIVE_STATUS;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.API_TIMEOUT;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.INACTIVE_STATUS;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.computeStatusSummary;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.fetchAndMergeCapabilities;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.printCapabilities;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

/**
 * CLI command for checking the availability status of registered capabilities.
 *
 * <p>This command provides a focused view of capability availability, showing
 * a summary of active, inactive, and unknown capabilities along with a filtered
 * table of capabilities matching the requested status.</p>
 *
 * <p>Usage examples:</p>
 * <pre>
 * # Show status summary for all capabilities
 * wanaku capabilities status
 *
 * # Show only inactive capabilities
 * wanaku capabilities status --filter inactive
 *
 * # Show only active capabilities from a specific host
 * wanaku capabilities status --host http://remote:8080 --filter active
 * </pre>
 *
 * @see CapabilitiesList
 * @see CapabilitiesHelper
 */
@Command(name = "status", description = "Show the availability status of registered capabilities")
public class CapabilitiesStatus extends BaseCommand {

    @Option(
            names = {"--host"},
            description = "The API host URL (default: http://localhost:8080)",
            defaultValue = "http://localhost:8080")
    private String host;

    @Option(
            names = {"--filter"},
            description = "Filter by status: active, inactive, or unknown (default: show all)")
    private String filter;

    private CapabilitiesService capabilitiesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        capabilitiesService = initService(CapabilitiesService.class, host);

        List<PrintableCapability> capabilities =
                fetchAndMergeCapabilities(capabilitiesService).await().atMost(API_TIMEOUT);

        if (capabilities.isEmpty()) {
            printer.printInfoMessage("No capabilities registered.");
            return EXIT_OK;
        }

        StatusSummary summary = computeStatusSummary(capabilities);

        // Print summary
        printer.printInfoMessage("Capability Status Summary:");
        printer.printSuccessMessage(String.format("  Active:   %d", summary.active()));
        if (summary.inactive() > 0) {
            printer.printErrorMessage(String.format("  Inactive: %d", summary.inactive()));
        } else {
            printer.printInfoMessage(String.format("  Inactive: %d", summary.inactive()));
        }
        printer.printWarningMessage(String.format("  Unknown:  %d", summary.unknown()));
        printer.printInfoMessage(String.format("  Total:    %d", summary.total()));
        System.out.println();

        // Apply filter if requested
        List<PrintableCapability> filtered = applyFilter(capabilities);
        if (filtered.isEmpty()) {
            printer.printWarningMessage("No capabilities match the filter: " + filter);
            return EXIT_OK;
        }

        printCapabilities(filtered, printer);
        return EXIT_OK;
    }

    private List<PrintableCapability> applyFilter(List<PrintableCapability> capabilities) {
        if (filter == null || filter.isBlank()) {
            return capabilities;
        }

        return switch (filter.toLowerCase()) {
            case "active" ->
                capabilities.stream()
                        .filter(c -> ACTIVE_STATUS.equals(c.status()))
                        .toList();
            case "inactive" ->
                capabilities.stream()
                        .filter(c -> INACTIVE_STATUS.equals(c.status()))
                        .toList();
            case "unknown" ->
                capabilities.stream()
                        .filter(c -> !ACTIVE_STATUS.equals(c.status()) && !INACTIVE_STATUS.equals(c.status()))
                        .toList();
            default -> capabilities;
        };
    }
}
