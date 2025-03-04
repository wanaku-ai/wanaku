package ai.wanaku.cli.main.commands.targets.tools;

import java.util.Map;

import ai.wanaku.api.types.management.Service;
import ai.wanaku.cli.main.commands.targets.AbstractTargetsList;
import ai.wanaku.cli.main.support.PrettyPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "list",
        description = "List targeted services")
public class ToolsLinkedList extends AbstractTargetsList {
    @Override
    public void run() {
        initService();

        Map<String, Service> list = linkService.toolsList();
        PrettyPrinter.printTargets(list);
    }
}
