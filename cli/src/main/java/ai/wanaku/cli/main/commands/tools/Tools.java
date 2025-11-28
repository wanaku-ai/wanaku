package ai.wanaku.cli.main.commands.tools;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Parent command for managing tools in the Wanaku platform.
 * <p>
 * This command provides access to tool management operations including:
 * </p>
 * <ul>
 *   <li>Adding new tools to the system</li>
 *   <li>Listing and filtering existing tools</li>
 *   <li>Editing tool configurations</li>
 *   <li>Removing tools</li>
 *   <li>Importing tools from external sources</li>
 *   <li>Generating tool definitions</li>
 *   <li>Managing tool labels</li>
 * </ul>
 * <p>
 * When invoked without subcommands, displays available tool management options.
 * </p>
 */
@CommandLine.Command(
        name = "tools",
        description = "Manage tools",
        subcommands = {
            ToolsEdit.class,
            ToolsAdd.class,
            ToolsRemove.class,
            ToolsList.class,
            ToolsShow.class,
            ToolsImport.class,
            ToolsGenerate.class,
            ToolsLabel.class
        })
public class Tools extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_OK;
    }
}
