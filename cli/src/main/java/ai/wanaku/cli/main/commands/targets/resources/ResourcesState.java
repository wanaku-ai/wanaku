package ai.wanaku.cli.main.commands.targets.resources;

import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.api.types.discovery.ServiceState;
import ai.wanaku.api.types.providers.ServiceTarget;
import java.util.List;
import java.util.Map;

import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.targets.AbstractTargetsList;
import ai.wanaku.cli.main.support.PrettyPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "state",
        description = "List service states")
public class ResourcesState extends AbstractTargetsList {
    @Override
    public void run() {
        initService();

        Map<String, List<ActivityRecord>> states = targetsService.resourcesState().data();
        PrettyPrinter.printStates(states);
    }
}
