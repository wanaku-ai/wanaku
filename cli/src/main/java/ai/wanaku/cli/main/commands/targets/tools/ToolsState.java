package ai.wanaku.cli.main.commands.targets.tools;

import java.util.List;
import java.util.Map;

import ai.wanaku.api.types.management.State;
import ai.wanaku.cli.main.commands.targets.AbstractTargetState;
import ai.wanaku.cli.main.support.PrettyPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "state",
        description = "List services states")
public class ToolsState extends AbstractTargetState {
    @Override
    public void run() {
        initService();

        Map<String, List<State>> states = linkService.toolsState().data();
        PrettyPrinter.printStates(states);
    }
}
