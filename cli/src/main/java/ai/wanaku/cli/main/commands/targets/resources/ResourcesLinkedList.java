package ai.wanaku.cli.main.commands.targets.resources;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.cli.main.commands.targets.AbstractTargetsList;
import ai.wanaku.cli.main.support.PrettyPrinter;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name = "list",
        description = "List targeted services")
public class ResourcesLinkedList extends AbstractTargetsList {
    @Override
    public Integer call() {
        initService();

        List<ServiceTarget> list = targetsService.resourcesList().data();
        PrettyPrinter.printTargets(list);
        return EXIT_OK;
    }
}
