package ai.wanaku.cli.main.commands.datastores;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Parent command for managing data store labels.
 * <p>
 * This command provides subcommands for adding and removing labels from existing data stores.
 * Labels can be used to organize, categorize, and filter data stores for easier management.
 * </p>
 *
 * @see DataStoresLabelAdd
 * @see DataStoresLabelRemove
 */
@CommandLine.Command(
        name = "label",
        description = "Manage data store labels",
        subcommands = {DataStoresLabelAdd.class, DataStoresLabelRemove.class})
public class DataStoresLabel extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_OK;
    }
}
