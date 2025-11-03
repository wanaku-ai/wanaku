package ai.wanaku.cli.main.commands.namespaces;

import static picocli.CommandLine.usage;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Parent command for managing namespaces in the Wanaku platform.
 * <p>
 * This command provides access to namespace management operations including:
 * </p>
 * <ul>
 *   <li>Listing available namespaces</li>
 * </ul>
 * <p>
 * Namespaces provide logical grouping and isolation for tools and other resources,
 * allowing multiple instances of the same tool name to coexist in different namespaces.
 * </p>
 */
@CommandLine.Command(
        name = "namespaces",
        description = "Manage namespaces",
        subcommands = {NamespaceList.class, NamespacesLabel.class})
public class Namespaces extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        usage(this, System.out);
        return EXIT_ERROR;
    }
}
