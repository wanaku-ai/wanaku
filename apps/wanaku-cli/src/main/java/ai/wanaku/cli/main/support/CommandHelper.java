package ai.wanaku.cli.main.support;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jline.prompt.ConfirmResult;
import org.jline.prompt.Prompt;
import org.jline.prompt.PromptBuilder;
import org.jline.prompt.PromptResult;
import org.jline.prompt.Prompter;
import org.jline.prompt.PrompterFactory;
import org.jline.terminal.Terminal;

/**
 * Utility class providing helper methods for command-line interactions.
 * <p>
 * This class offers convenient methods for prompting users for confirmation
 * and other interactive command-line operations using JLine terminal capabilities.
 * </p>
 */
public class CommandHelper {

    /**
     * Prompts the user for confirmation to perform a certain action.
     *
     * @param terminal The JLine terminal instance.
     * @param message     the message to be displayed to inform the user.
     * @return true if the user confirms, false otherwise.
     * @throws IOException if an I/O error occurs during the prompt.
     */
    public static boolean confirm(Terminal terminal, String message) throws IOException {
        Prompter prompter = PrompterFactory.create(terminal);
        PromptBuilder builder = prompter.newBuilder();
        // Create a simple yes/no prompt

        builder.createConfirmPrompt()
                .name("continue")
                .message(message)
                .defaultValue(false)
                .addPrompt();

        Map<String, ? extends PromptResult<? extends Prompt>> result = prompter.prompt(List.of(), builder.build());
        ConfirmResult confirmResult = (ConfirmResult) result.get("continue");
        return confirmResult.isConfirmed();
    }
}
