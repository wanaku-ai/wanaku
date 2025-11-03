package ai.wanaku.cli.main.commands.resources;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Parent command for managing resource labels.
 * <p>
 * This command provides subcommands for adding and removing labels from existing resources.
 * Labels can be used to organize, categorize, and filter resources for easier management.
 * </p>
 *
 * @see ResourcesLabelAdd
 * @see ResourcesLabelRemove
 */
@CommandLine.Command(
        name = "label",
        description = "Manage resource labels",
        subcommands = {ResourcesLabelAdd.class, ResourcesLabelRemove.class})
public class ResourcesLabel extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_OK;
    }
}
