package ai.wanaku.cli.main.commands.targets.resources;

import java.util.Map;

import ai.wanaku.api.types.management.Service;
import ai.wanaku.cli.main.commands.targets.AbstractTargetsList;
import ai.wanaku.cli.main.support.PrettyPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "list",
        description = "List targeted services")
public class ResourcesLinkedList extends AbstractTargetsList {
    @Override
    public void run() {
        initService();

        Map<String, Service> list = linkService.resourcesList();
        PrettyPrinter.printTargets(list);
    }
}
