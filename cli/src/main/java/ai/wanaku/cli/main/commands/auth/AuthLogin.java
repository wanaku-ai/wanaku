package ai.wanaku.cli.main.commands.auth;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(name = "login", description = "Interactive authentication login")
public class AuthLogin extends BaseCommand {

    @CommandLine.Option(
            names = {"--token"},
            description = "API token for authentication")
    private String apiToken;

    @CommandLine.Option(
            names = {"--auth-server"},
            description = "Authentication server URL")
    private String authServerUrl;

    @CommandLine.Option(
            names = {"--mode"},
            description = "Authentication mode (token, oauth2)",
            defaultValue = "token")
    private String authMode;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        AuthCredentialStore credentialStore = new AuthCredentialStore();

        if (apiToken != null) {
            credentialStore.storeApiToken(apiToken);
            credentialStore.storeAuthMode(authMode);

            if (authServerUrl != null) {
                credentialStore.storeAuthServerUrl(authServerUrl);
            }

            printer.printSuccessMessage("Successfully stored authentication credentials");
            return EXIT_OK;
        }

        if ("oauth2".equals(authMode)) {
            printer.printErrorMessage("OAuth2 authentication not yet implemented. Use --token option for now.");
            return EXIT_ERROR;
        }

        printer.printInfoMessage("Interactive login mode");
        printer.printInfoMessage("To login with an API token, use: wanaku auth login --token <your-token>");

        return EXIT_OK;
    }
}
