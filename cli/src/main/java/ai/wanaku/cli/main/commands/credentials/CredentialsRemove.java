package ai.wanaku.cli.main.commands.credentials;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.admin.BaseAdminCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import picocli.CommandLine;

@CommandLine.Command(name = "remove", description = "Delete a service client")
public class CredentialsRemove extends BaseAdminCommand {

    @CommandLine.Option(
            names = {"--client-id"},
            description = "Client ID of the service client to delete",
            required = true)
    private String clientId;

    public CredentialsRemove() {
        super();
    }

    public CredentialsRemove(AuthCredentialStore credentialStore) {
        super(credentialStore);
    }

    public CredentialsRemove(AuthCredentialStore credentialStore, KeycloakAdminClient adminClient) {
        super(credentialStore, adminClient);
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            KeycloakAdminClient client = createAdminClient();
            client.deleteClient(realm, clientId);
            printer.printSuccessMessage("Client '" + clientId + "' deleted successfully");
            return EXIT_OK;
        } catch (KeycloakAdminClient.KeycloakAdminException e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }
}
