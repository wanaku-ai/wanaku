package ai.wanaku.cli.main.commands.admin;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.commands.credentials.Credentials;
import ai.wanaku.cli.main.commands.users.Users;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "admin",
        description = "Keycloak administration commands",
        subcommands = {Users.class, Credentials.class})
public class Admin extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
