package ai.wanaku.cli.main.commands.credentials;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.admin.BaseAdminCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import picocli.CommandLine;

@CommandLine.Command(name = "add", description = "Create a new service client")
public class CredentialsAdd extends BaseAdminCommand {

    @CommandLine.Option(
            names = {"--client-id"},
            description = "Client ID for the new service client",
            required = true)
    private String clientId;

    @CommandLine.Option(
            names = {"--description"},
            description = "Description for the service client")
    private String description;

    @CommandLine.Option(
            names = {"--show-secret"},
            description = "Print client secret to stdout (use with caution; may leak into logs or shell history)")
    private boolean showSecret;

    public CredentialsAdd() {
        super();
    }

    public CredentialsAdd(KeycloakAdminClient adminClient) {
        super(adminClient);
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            KeycloakAdminClient client = createAdminClient();
            client.createClient(realm, clientId, description);
            printer.printSuccessMessage("Client '" + clientId + "' created successfully");

            if (showSecret) {
                String secret = client.getClientSecret(realm, clientId);
                if (secret != null) {
                    printer.printInfoMessage("Client Secret: " + secret);
                }
            }

            return EXIT_OK;
        } catch (KeycloakAdminClient.KeycloakAdminException e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }
}
