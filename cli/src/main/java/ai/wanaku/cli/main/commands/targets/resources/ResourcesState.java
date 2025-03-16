package ai.wanaku.cli.main.commands.targets.resources;

import java.util.List;
import java.util.Map;

import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.management.State;
import ai.wanaku.cli.main.commands.targets.AbstractTargetsList;
import ai.wanaku.cli.main.support.PrettyPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "state",
        description = "List service states")
public class ResourcesState extends AbstractTargetsList {
    @Override
    public void run() {
        initService();

        WanakuResponse<Map<String, List<State>>> list = linkService.resourcesState();
        PrettyPrinter.printStates(list.data());
    }
}
