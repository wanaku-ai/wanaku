package ai.wanaku.cli.main.commands.targets.resources;

import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.cli.main.commands.targets.AbstractTargets;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.CapabilitiesService;
import picocli.CommandLine;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static ai.wanaku.cli.main.support.TargetsHelper.getPrintableTargets;

@CommandLine.Command(name = "state",
        description = "List service states")
@Deprecated
public class ResourcesState extends AbstractTargets {

    /**
     * Standard column names for resource states display.
     * <p>This array defines the order and names of columns when displaying
     * resources in table or map format. The order matches the fields
     * in {@link ResourcesState}.
     */
    public static final String [] COLUMNS = {"id", "service", "active", "lastSeen"};

    @Override
    public Integer doTargetCall(WanakuPrinter printer) throws IOException {
        CapabilitiesService capabilitiesService = initService(CapabilitiesService.class, host);
        Map<String, List<ActivityRecord>> states = capabilitiesService.resourcesState().data();
        List<Map<String, String>> printableStates = getPrintableTargets(states);
        printer.printTable(printableStates, COLUMNS);
        return EXIT_OK;
    }
}
