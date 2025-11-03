package ai.wanaku.cli.main.commands.man;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.DocumentationLoader;
import ai.wanaku.cli.main.support.WanakuPrinter;
import java.io.IOException;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

/**
 * Command to display label expression documentation.
 *
 * <p>This command loads and displays the comprehensive label expressions guide
 * in an interactive pager with terminal formatting using Markdown rendering.</p>
 *
 * <p>The documentation is displayed using a less-like pager that supports:</p>
 * <ul>
 *   <li>Page-by-page navigation (Space/b)</li>
 *   <li>Line-by-line scrolling (j/k or arrow keys)</li>
 *   <li>Search functionality (/pattern)</li>
 *   <li>Jump to beginning/end (g/G)</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * wanaku man label-expression
 * }</pre>
 */
@CommandLine.Command(
        name = "label-expression",
        description = "Display label expression documentation",
        mixinStandardHelpOptions = true)
public class LabelExpressionMan extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            String markdown = DocumentationLoader.loadLabelExpressionsDoc();
            printer.pageMarkdown(markdown);
            return EXIT_OK;
        } catch (IOException e) {
            printer.printErrorMessage("Failed to load documentation: " + e.getMessage());
            return EXIT_ERROR;
        }
    }
}
