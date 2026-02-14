package ai.wanaku.cli.main.commands.completion;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

import static picocli.CommandLine.usage;

/**
 * Parent command for managing shell completion scripts.
 * <p>
 * This command provides access to shell completion generation operations for
 * bash and zsh shells supported by picocli.
 * </p>
 */
@CommandLine.Command(
        name = "completion",
        description = "Generate shell completion scripts",
        subcommands = {CompletionGenerate.class})
public class Completion extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        usage(this, System.out);
        return EXIT_ERROR;
    }
}
