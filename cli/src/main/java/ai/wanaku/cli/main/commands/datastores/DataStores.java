package ai.wanaku.cli.main.commands.datastores;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

/**
 * Main command for managing data stores.
 */
@CommandLine.Command(
        name = "data-store",
        description = "Manage data stores",
        subcommands = {
            DataStoresAdd.class,
            DataStoresGet.class,
            DataStoresList.class,
            DataStoresRemove.class,
            DataStoresLabel.class
        })
public class DataStores extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_OK;
    }
}
