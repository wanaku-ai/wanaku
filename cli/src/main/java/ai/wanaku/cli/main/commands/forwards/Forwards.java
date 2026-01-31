package ai.wanaku.cli.main.commands.forwards;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.usage;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import java.io.IOException;
import org.jline.terminal.Terminal;

/**
 * Parent command for managing port forwarding configurations in the Wanaku platform.
 * <p>
 * This command provides access to forward management operations including:
 * </p>
 * <ul>
 *   <li>Adding new port forwarding rules</li>
 *   <li>Listing existing forwards</li>
 *   <li>Removing forwards</li>
 * </ul>
 * <p>
 * Forwards enable network connectivity between AI agents and external services
 * by creating port forwarding rules for containerized workloads.
 * </p>
 */
@Command(
        name = "forwards",
        description = "Manage forwards",
        subcommands = {ForwardsAdd.class, ForwardsRemove.class, ForwardsList.class, ForwardsRefresh.class})
public class Forwards extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        usage(this, System.out);
        return EXIT_ERROR;
    }
}
