package ai.wanaku.cli.main.commands.targets.tools;

import static ai.wanaku.cli.main.support.TargetsHelper.getPrintableTargets;

import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.cli.main.commands.targets.AbstractTargets;
import ai.wanaku.cli.main.support.WanakuPrinter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;

@Command(name = "state", description = "List services states")
@Deprecated
public class ToolsState extends AbstractTargets {

    private static final String[] COLUMS = {"id", "service", "active", "lastSeen"};

    /**
     * Executes the tools state command.
     *
     * This method performs the following operations:
     * <ol>
     *   <li>Initializes the targets service</li>
     *   <li>Retrieves the current tools state from the service</li>
     *   <li>Formats and prints the tools state in a tabular format</li>
     * </ol>
     *
     * @return {@link #EXIT_OK} (0) on successful execution, or appropriate error code
     *         if an exception occurs during processing
     * @throws IOException if there's an I/O error while creating the terminal instance
     *                     or communicating with the targets service
     * @throws RuntimeException if the targets service initialization fails or
     *                          if there's an error retrieving the tools state
     */
    @Override
    protected Integer doTargetCall(WanakuPrinter printer) throws Exception {
        Map<String, List<ActivityRecord>> states =
                capabilitiesService.toolsState().data();
        List<Map<String, String>> printableStates = getPrintableTargets(states);
        printer.printTable(printableStates, COLUMS);
        return EXIT_OK;
    }
}
