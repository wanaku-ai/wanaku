package ai.wanaku.cli.main.commands.targets.resources;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.cli.main.commands.targets.AbstractTargets;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name = "list",
        description = "List targeted services")
@Deprecated
public class ResourcesLinkedList extends AbstractTargets {

    @Override
    protected Integer doTargetCall(WanakuPrinter printer) throws Exception {
        List<ServiceTarget> list = targetsService.resourcesList().data();
        printer.printTable(list, "id", "service", "host");
        return EXIT_OK;
    }
}
