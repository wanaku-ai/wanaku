package ai.wanaku.cli.main.commands.credentials;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.admin.BaseAdminCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import picocli.CommandLine;

@CommandLine.Command(name = "regenerate", description = "Regenerate secret for a service client")
public class CredentialsRegenerate extends BaseAdminCommand {

    @CommandLine.Option(
            names = {"--client-id"},
            description = "Client ID of the service client",
            required = true)
    private String clientId;

    @CommandLine.Option(
            names = {"--show-secret"},
            description = "Print regenerated secret to stdout (use with caution; may leak into logs or shell history)")
    private boolean showSecret;

    public CredentialsRegenerate() {
        super();
    }

    public CredentialsRegenerate(KeycloakAdminClient adminClient) {
        super(adminClient);
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            KeycloakAdminClient client = createAdminClient();
            client.regenerateClientSecret(realm, clientId);
            printer.printSuccessMessage("Secret regenerated for client '" + clientId + "'");

            if (showSecret) {
                String secret = client.getClientSecret(realm, clientId);
                if (secret != null) {
                    printer.printInfoMessage("New Secret: " + secret);
                }
            }

            return EXIT_OK;
        } catch (KeycloakAdminClient.KeycloakAdminException e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }
}
