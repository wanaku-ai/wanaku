package ai.wanaku.cli.main.commands.users;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.admin.BaseAdminCommand;
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
            interactive = true,
            arity = "0..1")
    private String password;

    @CommandLine.Option(
            names = {"--email"},
            description = "Email address for the new user")
    private String email;

    public UsersAdd() {
        super();
    }

    public UsersAdd(KeycloakAdminClient adminClient) {
        super(adminClient);
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
