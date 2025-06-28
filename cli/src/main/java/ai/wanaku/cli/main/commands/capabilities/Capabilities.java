package ai.wanaku.cli.main.commands.capabilities;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

import java.io.IOException;

@CommandLine.Command(name = "capabilities",
        description = "Manage capabilities", subcommands = { CapabilitiesList.class, CapabilitiesCreate.class, CapabilitiesShow.class })
public class Capabilities extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}