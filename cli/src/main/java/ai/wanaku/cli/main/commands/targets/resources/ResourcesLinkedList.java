package ai.wanaku.cli.main.commands.targets.resources;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.cli.main.commands.targets.AbstractTargetsList;
import ai.wanaku.cli.main.support.PrettyPrinter;
import java.util.List;
import picocli.CommandLine;

@CommandLine.Command(name = "list",
        description = "List targeted services")
public class ResourcesLinkedList extends AbstractTargetsList {
    @Override
    public void run() {
        initService();

        List<ServiceTarget> list = targetsService.resourcesList().data();
        PrettyPrinter.printTargets(list);
    }
}
