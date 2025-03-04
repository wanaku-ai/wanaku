package ai.wanaku.cli.main.commands.targets.resources;

import ai.wanaku.cli.main.commands.targets.AbstractTargetsUnlink;
import picocli.CommandLine;

@CommandLine.Command(name = "unlink",
        description = "Unlink services from a target")
public class ResourcesUnlink extends AbstractTargetsUnlink {
    @Override
    public void run() {
        initService();

        linkService.resourcesUnlink(service);
    }
}
