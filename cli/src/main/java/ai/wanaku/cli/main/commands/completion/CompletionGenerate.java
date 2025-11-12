package ai.wanaku.cli.main.commands.completion;

import ai.wanaku.cli.main.CliMain;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.jline.terminal.Terminal;
import picocli.AutoComplete;
import picocli.CommandLine;

/**
 * Command to generate shell completion scripts for bash and zsh.
 * <p>
 * Supports generating completion scripts for:
 * </p>
 * <ul>
 *   <li>bash - Bash shell completion</li>
 *   <li>zsh - Zsh shell completion (uses bash completion via bashcompinit)</li>
 * </ul>
 * <p>
 * The generated completion script includes all subcommands, options, and their descriptions,
 * enabling tab-completion for the entire Wanaku CLI.
 * </p>
 *
 * <h2>Usage Examples</h2>
 * <pre>
 * # Generate bash completion script to stdout
 * wanaku completion generate
 *
 * # Generate and save bash completion script
 * wanaku completion generate --output /etc/bash_completion.d/wanaku_completion
 *
 * # Generate zsh completion script (same format works for both)
 * wanaku completion generate --output ~/.zsh/completions/_wanaku
 * </pre>
 *
 * <h2>Installation</h2>
 * <p>
 * After generating the completion script, you need to source it in your shell:
 * </p>
 * <pre>
 * # For bash (add to ~/.bashrc)
 * source /etc/bash_completion.d/wanaku_completion
 *
 * # For zsh (add to ~/.zshrc)
 * autoload -U +X bashcompinit && bashcompinit
 * source ~/.zsh/completions/_wanaku
 * </pre>
 */
@CommandLine.Command(name = "generate", description = "Generate shell completion script for bash and zsh")
public class CompletionGenerate extends BaseCommand {

    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "Output file path (default: stdout)")
    private File outputFile;

    @CommandLine.Option(
            names = {"-n", "--name"},
            description = "Command name for completion (default: wanaku)")
    private String commandName = "wanaku";

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            // Create a CommandLine instance for the main CLI command
            CommandLine commandLine = new CommandLine(CliMain.class);

            // Generate the completion script
            String completionScript = AutoComplete.bash(commandName, commandLine);

            // Output the script
            if (outputFile != null) {
                // Generate to file
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        printer.println("Error: Failed to create directory: " + parentDir);
                        return EXIT_ERROR;
                    }
                }

                try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
                    writer.write(completionScript);
                }
                printer.println("Completion script generated: " + outputFile.getAbsolutePath());
                printInstallationInstructions(printer, outputFile.getAbsolutePath());
            } else {
                // Generate to stdout
                System.out.println(completionScript);
            }

            return EXIT_OK;
        } catch (IOException e) {
            printer.println("Error generating completion script: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    /**
     * Prints installation instructions for the generated completion script.
     *
     * @param printer the printer to output instructions to
     * @param outputPath the path where the completion script was saved
     */
    private void printInstallationInstructions(WanakuPrinter printer, String outputPath) {
        printer.println("");
        printer.println("Installation instructions:");
        printer.println("");
        printer.println("For Bash:");
        printer.println("  Add the following line to your ~/.bashrc:");
        printer.println("    source " + outputPath);
        printer.println("");
        printer.println("  Then reload your shell:");
        printer.println("    source ~/.bashrc");
        printer.println("");
        printer.println("For Zsh:");
        printer.println("  Add the following lines to your ~/.zshrc:");
        printer.println("    autoload -U +X bashcompinit && bashcompinit");
        printer.println("    source " + outputPath);
        printer.println("");
        printer.println("  Then reload your shell:");
        printer.println("    source ~/.zshrc");
    }
}
