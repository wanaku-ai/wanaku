package ai.wanaku.cli.main.commands.prompts;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "prompts",
        description = "Manage prompts",
        subcommands = {PromptsAdd.class, PromptsEdit.class, PromptsRemove.class, PromptsList.class})
public class Prompts extends BaseCommand {
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_OK;
    }
}
