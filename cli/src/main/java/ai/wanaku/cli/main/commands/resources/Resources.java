package ai.wanaku.cli.main.commands.resources;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import java.io.IOException;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Parent command for managing resources in the Wanaku platform.
 * <p>
 * This command provides access to resource management operations including:
 * </p>
 * <ul>
 *   <li>Exposing resources to make them available to AI agents</li>
 *   <li>Listing and filtering existing resources</li>
 *   <li>Removing resources from the system</li>
 *   <li>Managing resource labels for organization and filtering</li>
 * </ul>
 * <p>
 * Resources represent data sources, APIs, or other external systems that
 * can be accessed by AI agents during tool execution.
 * </p>
 */
@CommandLine.Command(
        name = "resources",
        description = "Manage resources",
        subcommands = {ResourcesExpose.class, ResourcesRemove.class, ResourcesList.class, ResourcesLabel.class})
public class Resources extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
