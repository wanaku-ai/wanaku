package ai.wanaku.cli.main.commands.namespaces;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Parent command for managing namespace labels.
 * <p>
 * This command provides subcommands for adding and removing labels from existing namespaces.
 * Labels can be used to organize, categorize, and filter namespaces for easier management.
 * </p>
 *
 * @see NamespacesLabelAdd
 * @see NamespacesLabelRemove
 */
@CommandLine.Command(
        name = "label",
        description = "Manage namespace labels",
        subcommands = {NamespacesLabelAdd.class, NamespacesLabelRemove.class})
public class NamespacesLabel extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_OK;
    }
}
