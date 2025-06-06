package ai.wanaku.cli.main.commands.targets.tools;

import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.api.types.discovery.ServiceState;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.cli.main.commands.targets.AbstractTargetState;
import ai.wanaku.cli.main.support.PrettyPrinter;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;

@CommandLine.Command(name = "state",
        description = "List services states")
public class ToolsState extends AbstractTargetState {
    @Override
    public void run() {
        initService();

        Map<String, List<ActivityRecord>> states = targetsService.toolsState().data();
        PrettyPrinter.printStates(states);
    }
}
