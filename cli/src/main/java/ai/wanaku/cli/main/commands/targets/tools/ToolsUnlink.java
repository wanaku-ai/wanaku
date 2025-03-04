package ai.wanaku.cli.main.commands.targets.tools;

import ai.wanaku.cli.main.commands.targets.AbstractTargetsUnlink;
import picocli.CommandLine;

@CommandLine.Command(name = "unlink",
        description = "Unlink services from a target")
public class ToolsUnlink extends AbstractTargetsUnlink {
    @Override
    public void run() {
        initService();

        linkService.toolsUnlink(service);
    }
}
