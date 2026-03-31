package ai.wanaku.cli.main.commands.configure;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "configure",
        description = "Configure popular MCP clients to connect to Wanaku",
        subcommands = {ConfigureClaude.class, ConfigureCursor.class})
public class Configure extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
