package ai.wanaku.cli.main.commands.users;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.admin.BaseAdminCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import picocli.CommandLine;

@CommandLine.Command(name = "add", description = "Create a new Keycloak user")
public class UsersAdd extends BaseAdminCommand {

    @CommandLine.Option(
            names = {"--username"},
            description = "Username for the new user",
            required = true)
    private String username;

    @CommandLine.Option(
            names = {"--password"},
            description = "Password for the new user",
            required = true,
            interactive = true)
    private String password;

    @CommandLine.Option(
            names = {"--email"},
            description = "Email address for the new user")
    private String email;

    public UsersAdd() {
        super();
    }

    public UsersAdd(AuthCredentialStore credentialStore) {
        super(credentialStore);
    }

    public UsersAdd(AuthCredentialStore credentialStore, KeycloakAdminClient adminClient) {
        super(credentialStore, adminClient);
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            KeycloakAdminClient client = createAdminClient();
            client.createUser(realm, username, password, email);
            printer.printSuccessMessage("User '" + username + "' created successfully");
            return EXIT_OK;
        } catch (KeycloakAdminClient.KeycloakAdminException e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }
}
