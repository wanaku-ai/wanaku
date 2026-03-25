package ai.wanaku.cli.main.commands.capabilities;

import java.io.IOException;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

/**
 * Parent command for creating new capability instances in the Wanaku platform.
 * <p>
 * This command provides subcommands for creating different types of capabilities:
 * </p>
 * <ul>
 *   <li>Tools - Create tool instances from capability providers</li>
 *   <li>Resources - Create resource instances from capability providers</li>
 * </ul>
 * <p>
 * Capabilities are instantiated from capability providers that advertise
 * their schemas and configuration requirements through the capabilities API.
 * </p>
 *
 * @see CapabilitiesCreateTool
 * @see CapabilitiesCreateResources
 */
@CommandLine.Command(
        name = "create",
        description = "Create a new capability",
        subcommands = {CapabilitiesCreateTool.class, CapabilitiesCreateResources.class})
public class CapabilitiesCreate extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
