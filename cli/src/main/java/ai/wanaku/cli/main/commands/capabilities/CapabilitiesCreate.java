package ai.wanaku.cli.main.commands.capabilities;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

import java.io.IOException;


@CommandLine.Command(name = "create",description = "Create a new capability",
        subcommands = { CapabilitiesCreateTool.class, CapabilitiesCreateResources.class})
public class CapabilitiesCreate extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
