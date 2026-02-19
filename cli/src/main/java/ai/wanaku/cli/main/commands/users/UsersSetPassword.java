package ai.wanaku.cli.main.commands.users;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.admin.BaseAdminCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import picocli.CommandLine;

@CommandLine.Command(name = "set-password", description = "Set password for a Keycloak user")
public class UsersSetPassword extends BaseAdminCommand {

    @CommandLine.Option(
            names = {"--username"},
            description = "Username of the user",
            required = true)
    private String username;

    @CommandLine.Option(
            names = {"--password"},
            description = "New password for the user",
            required = true,
            interactive = true)
    private String password;

    public UsersSetPassword() {
        super();
    }

    public UsersSetPassword(KeycloakAdminClient adminClient) {
        super(adminClient);
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            KeycloakAdminClient client = createAdminClient();
            client.setPassword(realm, username, password);
            printer.printSuccessMessage("Password updated for user '" + username + "'");
            return EXIT_OK;
        } catch (KeycloakAdminClient.KeycloakAdminException e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }
}
