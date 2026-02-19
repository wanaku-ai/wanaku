package ai.wanaku.cli.main.commands.users;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.admin.BaseAdminCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import picocli.CommandLine;

@CommandLine.Command(name = "remove", description = "Delete a Keycloak user")
public class UsersRemove extends BaseAdminCommand {

    @CommandLine.Option(
            names = {"--username"},
            description = "Username of the user to delete",
            required = true)
    private String username;

    public UsersRemove() {
        super();
    }

    public UsersRemove(KeycloakAdminClient adminClient) {
        super(adminClient);
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            KeycloakAdminClient client = createAdminClient();
            client.deleteUser(realm, username);
            printer.printSuccessMessage("User '" + username + "' deleted successfully");
            return EXIT_OK;
        } catch (KeycloakAdminClient.KeycloakAdminException e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }
}
