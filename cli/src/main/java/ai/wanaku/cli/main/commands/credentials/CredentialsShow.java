package ai.wanaku.cli.main.commands.credentials;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.admin.BaseAdminCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import picocli.CommandLine;

@CommandLine.Command(name = "show", description = "Show current secret for a service client")
public class CredentialsShow extends BaseAdminCommand {

    @CommandLine.Option(
            names = {"--client-id"},
            description = "Client ID of the service client",
            required = true)
    private String clientId;

    @CommandLine.Option(
            names = {"--show-secret"},
            description = "Print client secret to stdout (use with caution; may leak into logs or shell history)")
    private boolean showSecret;

    public CredentialsShow() {
        super();
    }

    public CredentialsShow(AuthCredentialStore credentialStore) {
        super(credentialStore);
    }

    public CredentialsShow(AuthCredentialStore credentialStore, KeycloakAdminClient adminClient) {
        super(credentialStore, adminClient);
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (!showSecret) {
            printer.printWarningMessage(
                    "Use --show-secret to display the client secret (use with caution; may leak into logs or shell history)");
            return EXIT_OK;
        }

        try {
            KeycloakAdminClient client = createAdminClient();
            String secret = client.getClientSecret(realm, clientId);
            if (secret != null) {
                printer.printInfoMessage("Client Secret: " + secret);
            } else {
                printer.printWarningMessage("No secret found for client '" + clientId + "'");
            }
            return EXIT_OK;
        } catch (KeycloakAdminClient.KeycloakAdminException e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }
}
