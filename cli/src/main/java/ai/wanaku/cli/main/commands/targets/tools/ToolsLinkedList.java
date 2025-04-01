package ai.wanaku.cli.main.commands.targets.tools;

import ai.wanaku.api.types.management.Service;
import ai.wanaku.cli.main.commands.targets.AbstractTargetsList;
import ai.wanaku.cli.main.support.PrettyPrinter;
import java.util.Map;
import picocli.CommandLine;

@CommandLine.Command(name = "list",
        description = "List targeted services")
public class ToolsLinkedList extends AbstractTargetsList {
    @Override
    public void run() {
        initService();

        Map<String, Service> list = targetsService.toolsList().data();
        PrettyPrinter.printTargets(list);
    }
}
