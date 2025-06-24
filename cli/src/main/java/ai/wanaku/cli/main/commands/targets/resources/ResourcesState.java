package ai.wanaku.cli.main.commands.targets.resources;

import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.cli.main.commands.targets.AbstractTargetsList;
import ai.wanaku.cli.main.support.PrettyPrinter;
import picocli.CommandLine;

import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "state",
        description = "List service states")
public class ResourcesState extends AbstractTargetsList {
    @Override
    public Integer call() {
        initService();

        Map<String, List<ActivityRecord>> states = targetsService.resourcesState().data();
        PrettyPrinter.printStates(states);
        return EXIT_OK;
    }
}
