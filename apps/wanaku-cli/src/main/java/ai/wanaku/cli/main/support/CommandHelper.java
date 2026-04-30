package ai.wanaku.cli.main.support;

import java.io.IOException;
import java.util.List;
import org.jline.terminal.Terminal;

public class CommandHelper {

    public static boolean confirm(Terminal terminal, String message) throws IOException {
        terminal.writer().print(message + " [y/N] ");
        terminal.writer().flush();
        int ch = terminal.reader().read();
        terminal.writer().println();
        terminal.writer().flush();
        return ch == 'y' || ch == 'Y';
    }

    public static int selectFromList(Terminal terminal, String message, List<String> items) throws IOException {
        terminal.writer().println(message);

        for (int i = 0; i < items.size(); i++) {
            terminal.writer().printf("  [%d] %s%n", i + 1, items.get(i));
        }
        terminal.writer().print("Enter selection (1-" + items.size() + "): ");
        terminal.writer().flush();

        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = terminal.reader().read();
            if (ch == '\n' || ch == '\r') {
                terminal.writer().println();
                break;
            }
            if (ch >= '0' && ch <= '9') {
                sb.append((char) ch);
                terminal.writer().print((char) ch);
                terminal.writer().flush();
            }
        }

        try {
            int selection = Integer.parseInt(sb.toString());
            if (selection >= 1 && selection <= items.size()) {
                return selection - 1;
            }
        } catch (NumberFormatException e) {
            // fall through
        }

        return 0;
    }
}
