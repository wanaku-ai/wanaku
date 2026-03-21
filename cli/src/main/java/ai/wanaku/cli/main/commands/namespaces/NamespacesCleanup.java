package ai.wanaku.cli.main.commands.namespaces;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.CommandHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.NamespacesService;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

/**
 * CLI command for cleaning up stale namespaces.
 */
@Command(name = "cleanup", description = "Find and remove stale namespaces")
public class NamespacesCleanup extends BaseCommand {

    private static final long SECONDS_PER_DAY = 86400;

    @Option(
            names = {"--host"},
            description = "The API host URL (default: http://localhost:8080)",
            defaultValue = "http://localhost:8080")
    String host;

    @Option(
            names = {"--max-age-days"},
            description = "Maximum age in days for pre-allocated namespaces (default: 7)",
            defaultValue = "7")
    int maxAgeDays;

    @Option(
            names = {"--include-assigned"},
            description = "Include assigned namespaces in stale evaluation")
    boolean includeAssigned;

    @Option(
            names = {"--include-unlabeled"},
            description = "Treat namespaces with missing labels as stale")
    boolean includeUnlabeled;

    @Option(
            names = {"-y", "--assume-yes"},
            description = "Automatically answer yes for all questions")
    boolean assumeYes;

    NamespacesService namespacesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        namespacesService = initService(NamespacesService.class, host);

        long maxAgeSeconds = maxAgeDays * SECONDS_PER_DAY;
        boolean unassignedOnly = !includeAssigned;

        try {
            WanakuResponse<List<Namespace>> response =
                    namespacesService.listStale(maxAgeSeconds, unassignedOnly, includeUnlabeled);
            List<Namespace> staleNamespaces = response.data();

            if (staleNamespaces == null || staleNamespaces.isEmpty()) {
                printer.printInfoMessage(buildNoStaleMessage());
                return EXIT_OK;
            }

            printer.printInfoMessage(buildStaleSummary(staleNamespaces.size()));
            printer.printTable(staleNamespaces, "id", "name", "path", "labels");

            boolean continueCleanup = true;
            if (!assumeYes) {
                continueCleanup =
                        CommandHelper.confirm(terminal, "Do you want to remove all stale namespaces listed above?");
            }

            if (!continueCleanup) {
                printer.printInfoMessage("Cleanup cancelled.");
                return EXIT_OK;
            }

            WanakuResponse<Integer> cleanupResponse =
                    namespacesService.cleanupStale(maxAgeSeconds, unassignedOnly, includeUnlabeled);
            int removedCount = cleanupResponse.data();

            printer.printSuccessMessage(String.format("Cleanup complete: %d namespace(s) removed", removedCount));
            return EXIT_OK;
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            commonResponseErrorHandler(response);
            return EXIT_ERROR;
        }
    }

    private String buildNoStaleMessage() {
        StringBuilder msg = new StringBuilder("No stale namespaces found");
        if (maxAgeDays > 0) {
            msg.append(" older than ").append(maxAgeDays).append(" day(s)");
        }
        if (!includeAssigned) {
            msg.append(" (unassigned only)");
        }
        if (includeUnlabeled) {
            msg.append(" (including unlabeled)");
        }
        return msg.toString();
    }

    private String buildStaleSummary(int count) {
        StringBuilder msg = new StringBuilder();
        msg.append("Found ").append(count).append(" stale namespace(s)");
        if (maxAgeDays > 0) {
            msg.append(" older than ").append(maxAgeDays).append(" day(s)");
        }
        if (!includeAssigned) {
            msg.append(" (unassigned only)");
        }
        if (includeUnlabeled) {
            msg.append(" (including unlabeled)");
        }
        msg.append(":");
        return msg.toString();
    }
}
