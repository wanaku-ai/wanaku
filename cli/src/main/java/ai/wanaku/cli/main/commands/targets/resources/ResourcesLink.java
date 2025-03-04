package ai.wanaku.cli.main.commands.targets.resources;

import ai.wanaku.cli.main.commands.targets.AbstractTargetsLink;
import picocli.CommandLine;

@CommandLine.Command(name = "link",
        description = "Link services to a target providing it")
public class ResourcesLink extends AbstractTargetsLink {

    @Override
    public void run() {
        initService();

        linkService.resourcesLink(service, target);
    }
}
