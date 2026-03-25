package ai.wanaku.cli.main.commands.auth;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "auth",
        description = "Authentication management commands",
        subcommands = {AuthLogin.class, AuthLogout.class, AuthStatus.class, AuthToken.class})
public class Auth extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
