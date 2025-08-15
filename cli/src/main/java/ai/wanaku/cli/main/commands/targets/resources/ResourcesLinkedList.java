package ai.wanaku.cli.main.commands.targets.resources;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.cli.main.commands.targets.AbstractTargets;
import ai.wanaku.cli.main.support.WanakuPrinter;
import java.util.List;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "List targeted services")
@Deprecated
public class ResourcesLinkedList extends AbstractTargets {

    @Override
    protected Integer doTargetCall(WanakuPrinter printer) throws Exception {
        List<ServiceTarget> list = capabilitiesService.resourcesList().data();
        printer.printTable(list, "id", "service", "host");
        return EXIT_OK;
    }
}
