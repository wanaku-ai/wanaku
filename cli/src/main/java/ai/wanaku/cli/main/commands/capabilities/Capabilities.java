package ai.wanaku.cli.main.commands.capabilities;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import java.io.IOException;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Parent command for managing capabilities in the Wanaku platform.
 * <p>
 * This command provides access to capability management operations including:
 * </p>
 * <ul>
 *   <li>Listing available capability providers</li>
 *   <li>Creating new capability instances (tools, resources, MCP servers)</li>
 *   <li>Viewing capability provider details and schemas</li>
 * </ul>
 * <p>
 * Capabilities represent the various features and functionality that can be
 * provided to AI agents, such as tool execution, resource access, and MCP protocol support.
 * </p>
 */
@CommandLine.Command(
        name = "capabilities",
        description = "Manage capabilities",
        subcommands = {
            CapabilitiesList.class,
            CapabilitiesCreate.class,
            CapabilitiesShow.class,
            CapabilitiesStatus.class,
            CapabilitiesWatch.class,
            CapabilitiesCleanup.class
        })
public class Capabilities extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
