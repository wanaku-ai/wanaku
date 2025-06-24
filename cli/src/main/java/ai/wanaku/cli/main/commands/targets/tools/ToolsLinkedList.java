package ai.wanaku.cli.main.commands.targets.tools;

import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.cli.main.commands.targets.AbstractTargetsList;
import ai.wanaku.cli.main.support.PrettyPrinter;
import java.util.List;
import picocli.CommandLine;

@CommandLine.Command(name = "list",
        description = "List targeted services")
public class ToolsLinkedList extends AbstractTargetsList {
    @Override
    public Integer call() {
        initService();

        List<ServiceTarget> list = targetsService.toolsList().data();
        PrettyPrinter.printTargets(list);
        return EXIT_OK;
    }
}
