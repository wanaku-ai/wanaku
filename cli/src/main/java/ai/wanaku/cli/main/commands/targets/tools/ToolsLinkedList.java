package ai.wanaku.cli.main.commands.targets.tools;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.cli.main.commands.targets.AbstractTargets;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

import java.io.IOException;
import java.util.List;

@CommandLine.Command(name = "list",
        description = "List targeted services")
@Deprecated
public class ToolsLinkedList extends AbstractTargets {

    @Override
    protected Integer doTargetCall(WanakuPrinter printer) throws IOException {
        List<ServiceTarget> list = capabilitiesService.toolsList().data();
        printer.printTable(list);
        return EXIT_OK;
    }
}
