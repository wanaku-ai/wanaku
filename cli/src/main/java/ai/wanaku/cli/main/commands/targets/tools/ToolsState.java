package ai.wanaku.cli.main.commands.targets.tools;

import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.cli.main.commands.targets.AbstractTargetState;
import ai.wanaku.cli.main.support.PrettyPrinter;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;

@CommandLine.Command(name = "state",
        description = "List services states")
public class ToolsState extends AbstractTargetState {
    @Override
    public Integer call() {
        initService();

        Map<String, List<ActivityRecord>> states = targetsService.toolsState().data();
        PrettyPrinter.printStates(states);
        return EXIT_OK;
    }
}
