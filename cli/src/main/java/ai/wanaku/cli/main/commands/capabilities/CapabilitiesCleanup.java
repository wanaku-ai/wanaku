package ai.wanaku.cli.main.commands.capabilities;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.CommandHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.CapabilitiesService;
import ai.wanaku.core.services.api.StaleCapabilityInfo;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

/**
 * CLI command for cleaning up stale capability registrations.
 * <p>
 * This command finds and removes capabilities that have not been seen (pinged)
 * within a specified time period. It supports the following modes:
 * </p>
 * <ul>
 *   <li><b>List only:</b> Preview stale capabilities without removing them</li>
 *   <li><b>Cleanup with confirmation:</b> Review stale capabilities, then confirm removal</li>
 *   <li><b>Automatic cleanup:</b> Remove stale capabilities without confirmation (using -y flag)</li>
 * </ul>
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * # List stale capabilities older than 1 day (default)
 * wanaku capabilities cleanup
 *
 * # Cleanup capabilities older than 7 days with confirmation
 * wanaku capabilities cleanup --max-age-days 7
 *
 * # Cleanup only inactive capabilities older than 1 day
 * wanaku capabilities cleanup --inactive-only
 *
 * # Automatic cleanup without confirmation
 * wanaku capabilities cleanup --max-age-days 1 -y
 * </pre>
 * <p>
 * <b>Warning:</b> Cleanup operations cannot be undone. Removed capabilities
 * will trigger deregistration SSE events for any connected clients.
 * </p>
 *
 * @see CapabilitiesService
 * @see CapabilitiesList
 */
@Command(name = "cleanup", description = "Find and remove stale capability registrations")
public class CapabilitiesCleanup extends BaseCommand {

    private static final long SECONDS_PER_DAY = 86400;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault());

    @Option(
            names = {"--host"},
            description = "The API host URL (default: http://localhost:8080)",
            defaultValue = "http://localhost:8080")
    private String host;

    @Option(
            names = {"--max-age-days"},
            description = "Maximum age in days since last seen (default: 1)",
            defaultValue = "1")
    private int maxAgeDays;

    @Option(
            names = {"--inactive-only"},
            description = "Only remove capabilities that are marked as inactive")
    private boolean inactiveOnly;

    @Option(
            names = {"-y", "--assume-yes"},
            description = "Automatically answer yes for all questions")
    private boolean assumeYes;

    private CapabilitiesService capabilitiesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        capabilitiesService = initService(CapabilitiesService.class, host);

        long maxAgeSeconds = maxAgeDays * SECONDS_PER_DAY;

        try {
            // Fetch stale capabilities
            WanakuResponse<List<StaleCapabilityInfo>> response =
                    capabilitiesService.listStale(maxAgeSeconds, inactiveOnly);
            List<StaleCapabilityInfo> staleCapabilities = response.data();

            if (staleCapabilities == null || staleCapabilities.isEmpty()) {
                printer.printInfoMessage(buildNoStaleMessage());
                return EXIT_OK;
            }

            // Show summary
            printer.printInfoMessage(String.format(
                    "Found %d stale capability(ies) older than %d day(s)%s:",
                    staleCapabilities.size(), maxAgeDays, inactiveOnly ? " (inactive only)" : ""));

            // Print table
            printStaleCapabilitiesTable(staleCapabilities, printer);

            // Confirm removal
            boolean continueCleanup = true;
            if (!assumeYes) {
                continueCleanup =
                        CommandHelper.confirm(terminal, "Do you want to remove all stale capabilities listed above?");
            }

            if (!continueCleanup) {
                printer.printInfoMessage("Cleanup cancelled.");
                return EXIT_OK;
            }

            // Perform cleanup
            WanakuResponse<Integer> cleanupResponse = capabilitiesService.cleanupStale(maxAgeSeconds, inactiveOnly);
            int removedCount = cleanupResponse.data();

            printer.printSuccessMessage(String.format("Cleanup complete: %d capability(ies) removed", removedCount));
            return EXIT_OK;

        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
    }

    private String buildNoStaleMessage() {
        StringBuilder msg = new StringBuilder("No stale capabilities found");
        if (maxAgeDays > 0) {
            msg.append(" older than ").append(maxAgeDays).append(" day(s)");
        }
        if (inactiveOnly) {
            msg.append(" (inactive only)");
        }
        return msg.toString();
    }

    private void printStaleCapabilitiesTable(List<StaleCapabilityInfo> capabilities, WanakuPrinter printer)
            throws IOException {
        // Format: id | serviceName | serviceType | host:port | active | lastSeen
        String[][] tableData = new String[capabilities.size() + 1][];

        // Header
        tableData[0] = new String[] {"ID", "Service", "Type", "Host:Port", "Active", "Last Seen"};

        // Data rows
        for (int i = 0; i < capabilities.size(); i++) {
            StaleCapabilityInfo cap = capabilities.get(i);
            String lastSeen = cap.lastSeen() != null ? DATE_FORMATTER.format(cap.lastSeen()) : "Never";
            String hostPort = cap.host() + ":" + cap.port();

            tableData[i + 1] = new String[] {
                truncate(cap.id(), 12),
                cap.serviceName(),
                cap.serviceType(),
                hostPort,
                cap.active() ? "Yes" : "No",
                lastSeen
            };
        }

        printSimpleTable(tableData, printer);
    }

    private void printSimpleTable(String[][] data, WanakuPrinter printer) throws IOException {
        if (data.length == 0) return;

        // Calculate column widths
        int[] widths = new int[data[0].length];
        for (String[] row : data) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i] != null ? row[i].length() : 0);
            }
        }

        // Build format string
        StringBuilder format = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) format.append(" | ");
            format.append("%-").append(widths[i]).append("s");
        }
        format.append("%n");

        // Print header
        System.out.printf(format.toString(), (Object[]) data[0]);

        // Print separator
        StringBuilder separator = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) separator.append("-+-");
            separator.append("-".repeat(widths[i]));
        }
        System.out.println(separator);

        // Print data rows
        for (int i = 1; i < data.length; i++) {
            System.out.printf(format.toString(), (Object[]) data[i]);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 3) + "...";
    }
}
