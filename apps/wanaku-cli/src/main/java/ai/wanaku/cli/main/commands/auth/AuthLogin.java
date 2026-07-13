package ai.wanaku.cli.main.commands.auth;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.security.CustomSecurityServiceConfig;
import ai.wanaku.cli.main.support.security.ServiceAuthenticator;
import ai.wanaku.cli.main.support.security.TokenEndpoint;
import picocli.CommandLine;

@CommandLine.Command(name = "login", description = "Interactive authentication login")
public class AuthLogin extends BaseCommand {

    private static final String DEFAULT_AUTH_SERVER = "http://localhost:8080";
    private static final String DEFAULT_CLIENT_ID = "admin-cli";
    private static final String DEFAULT_AUTH_MODE = "token";

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    AuthMode authMode;

    static class AuthMode {
        @CommandLine.Option(
                names = {"--api-token"},
                description = "API token for authentication")
        String apiToken;

        @CommandLine.ArgGroup(exclusive = false)
        UserPassCredentials credentials;
    }

    static class UserPassCredentials {
        @CommandLine.Option(
                names = {"--username"},
                required = true,
                description = "Username for authentication")
        String username;

        @CommandLine.Option(
                names = {"--password"},
                required = true,
                description = "Password for authentication",
                interactive = true)
        String password;
    }

    @CommandLine.Option(
            names = {"--auth-server"},
            description = "Authentication server URL (Wanaku or Keycloak)")
    private String authServerUrl;

    @CommandLine.Option(
            names = {"--realm"},
            description = "Authentication realm (e.g. 'wanaku'). When omitted, uses the router OIDC proxy.")
    private String realm;

    @CommandLine.Option(
            names = {"--client-id"},
            description = "OAuth2 client ID",
            defaultValue = DEFAULT_CLIENT_ID)
    private String clientId;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) {
        AuthCredentialStore credentialStore = new AuthCredentialStore();

        if (authMode.apiToken != null) {
            credentialStore.storeApiToken(authMode.apiToken);
            credentialStore.storeAuthMode(DEFAULT_AUTH_MODE);

            if (authServerUrl != null) {
                credentialStore.storeAuthServerUrl(authServerUrl);
            }

            printer.printSuccessMessage("Successfully stored authentication credentials");
            return EXIT_OK;
        }

        String serverUrl = authServerUrl != null ? authServerUrl : DEFAULT_AUTH_SERVER;

        try {
            printer.printInfoMessage("Authenticating with username and password...");
            CustomSecurityServiceConfig config = new CustomSecurityServiceConfig();
            config.setClientId(clientId);
            config.setUsername(authMode.credentials.username);
            config.setPassword(authMode.credentials.password);
            config.setTokenEndpoint(TokenEndpoint.forDiscovery(serverUrl, realm));
            ServiceAuthenticator serviceAuthenticator = new ServiceAuthenticator(config, insecure);

            credentialStore.storeApiToken(serviceAuthenticator.currentValidAccessToken());
            credentialStore.storeRefreshToken(serviceAuthenticator.currentValidRefreshToken());
            credentialStore.storeTokenExpiry(serviceAuthenticator.getTokenExpiryEpochSeconds());
            credentialStore.storeClientId(clientId);
            credentialStore.storeRealm(realm != null && realm.isBlank() ? null : realm);

            credentialStore.storeAuthMode("token");
            credentialStore.storeAuthServerUrl(serverUrl);

            printer.printSuccessMessage("Successfully authenticated and stored credentials");
            return EXIT_OK;
        } catch (Exception e) {
            printer.printErrorMessage("Authentication failed: " + e.getMessage());
            return EXIT_ERROR;
        }
    }
}
