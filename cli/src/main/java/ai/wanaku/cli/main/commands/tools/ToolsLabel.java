package ai.wanaku.cli.main.commands.tools;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Parent command for managing tool labels.
 * <p>
 * This command provides subcommands for adding and removing labels from existing tools.
 * Labels can be used to organize, categorize, and filter tools for easier management.
 * </p>
 *
 * @see ToolsLabelAdd
 * @see ToolsLabelRemove
 */
@CommandLine.Command(
        name = "label",
        description = "Manage tool labels",
        subcommands = {ToolsLabelAdd.class, ToolsLabelRemove.class})
public class ToolsLabel extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_OK;
    }
}
