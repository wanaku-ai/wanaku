package ai.wanaku.cli.main.support;

import java.io.IOException;
import java.util.Map;
import org.jline.consoleui.elements.ConfirmChoice;
import org.jline.consoleui.prompt.ConsolePrompt;
import org.jline.consoleui.prompt.PromptResultItemIF;
import org.jline.consoleui.prompt.builder.PromptBuilder;
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
        ConsolePrompt prompt = new ConsolePrompt(terminal);
        PromptBuilder builder = prompt.getPromptBuilder();
        // Create a simple yes/no prompt

        builder.createConfirmPromp()
                .name("continue")
                .message(message)
                .defaultValue(ConfirmChoice.ConfirmationValue.NO)
                .addPrompt();

        Map<String, PromptResultItemIF> result = prompt.prompt(builder.build());
        return "YES".equalsIgnoreCase(result.get("continue").getResult());
    }
}
