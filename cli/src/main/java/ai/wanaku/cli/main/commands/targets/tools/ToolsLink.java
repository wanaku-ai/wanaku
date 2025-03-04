package ai.wanaku.cli.main.commands.targets.tools;

import ai.wanaku.cli.main.commands.targets.AbstractTargetsLink;
import picocli.CommandLine;

@CommandLine.Command(name = "link",
        description = "Link services to a target providing it")
public class ToolsLink extends AbstractTargetsLink {

    @Override
    public void run() {
        initService();

        linkService.toolsLink(service, target);
    }
}
