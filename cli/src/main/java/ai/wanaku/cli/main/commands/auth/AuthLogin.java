package ai.wanaku.cli.main.commands.auth;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.security.auth.OidcTokenException;
import ai.wanaku.core.security.auth.OidcTokenService;
import ai.wanaku.core.security.auth.TokenResponse;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(name = "login", description = "Interactive authentication login")
public class AuthLogin extends BaseCommand {

    private static final String DEFAULT_AUTH_SERVER = "http://localhost:8543";
    private static final String DEFAULT_REALM = "wanaku";
    private static final String DEFAULT_CLIENT_ID = "admin-cli";
    private static final String DEFAULT_AUTH_MODE = "token";

    @CommandLine.Option(
            names = {"--api-token"},
            description = "API token for authentication")
    private String apiToken;

    @CommandLine.Option(
            names = {"--auth-server"},
            description = "Authentication server URL")
    private String authServerUrl;

    @CommandLine.Option(
            names = {"--username"},
            description = "Username for authentication")
    private String username;

    @CommandLine.Option(
            names = {"--password"},
            description = "Password for authentication",
            interactive = true)
    private String password;

    @CommandLine.Option(
            names = {"--realm"},
            description = "Authentication realm",
            defaultValue = DEFAULT_REALM)
    private String realm;

    @CommandLine.Option(
            names = {"--client-id"},
            description = "OAuth2 client ID",
            defaultValue = DEFAULT_CLIENT_ID)
    private String clientId;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        AuthCredentialStore credentialStore = new AuthCredentialStore();

        // Handle direct API token authentication
        if (apiToken != null) {
            credentialStore.storeApiToken(apiToken);
            credentialStore.storeAuthMode(DEFAULT_AUTH_MODE);

            if (authServerUrl != null) {
                credentialStore.storeAuthServerUrl(authServerUrl);
            }

            printer.printSuccessMessage("Successfully stored authentication credentials");
            return EXIT_OK;
        }

        // Handle username/password authentication
        if (username != null && password != null) {
            String serverUrl = authServerUrl != null ? authServerUrl : DEFAULT_AUTH_SERVER;

            try {
                printer.printInfoMessage("Authenticating with username and password...");
                OidcTokenService tokenService = new OidcTokenService();
                TokenResponse tokenResponse =
                        tokenService.retrieveToken(serverUrl, realm, clientId, username, password);

                credentialStore.storeApiToken(tokenResponse.getAccessToken());
                if (tokenResponse.getRefreshToken() != null) {
                    credentialStore.storeRefreshToken(tokenResponse.getRefreshToken());
                }
                credentialStore.storeAuthMode("token");
                credentialStore.storeAuthServerUrl(serverUrl);

                printer.printSuccessMessage("Successfully authenticated and stored credentials");
                return EXIT_OK;
            } catch (OidcTokenException e) {
                printer.printErrorMessage("Authentication failed: " + e.getMessage());
                return EXIT_ERROR;
            }
        }

        // Interactive mode - show help
        printer.printInfoMessage("Interactive login mode");
        printer.printInfoMessage("To login with an API token, use: wanaku auth login --api-token <your-token>");
        printer.printInfoMessage(
                "To login with username/password, use: wanaku auth login --username <user> --password <pass>");

        return EXIT_OK;
    }
}
