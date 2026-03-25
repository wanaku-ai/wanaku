package ai.wanaku.cli.main.commands.users;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "users",
        description = "Keycloak user management commands",
        subcommands = {UsersAdd.class, UsersList.class, UsersRemove.class, UsersSetPassword.class})
public class Users extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
