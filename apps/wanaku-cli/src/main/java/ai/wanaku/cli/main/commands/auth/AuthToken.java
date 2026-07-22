package ai.wanaku.cli.main.commands.auth;

import java.time.Instant;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.security.TokenRefresher;
import ai.wanaku.cli.main.support.security.TokenRefresher.RefreshResult;
import ai.wanaku.core.util.StringHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "token", description = "Manage API tokens")
public class AuthToken extends BaseCommand {

    /**
     * Buffer time in seconds before token expiry to trigger refresh.
     * Tokens will be refreshed when they are within this many seconds of expiring.
     */
    private static final long REFRESH_BUFFER_SECONDS = 30;

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    TokenOperation operation;

    static class TokenOperation {
        @CommandLine.Option(
                names = {"--set"},
                description = "Set the API token")
        String setToken;

        @CommandLine.ArgGroup(exclusive = false)
        GetOptions getOptions;

        @CommandLine.Option(
                names = {"--clear"},
                description = "Clear the API token")
        boolean clearToken;
    }

    static class GetOptions {
        @CommandLine.Option(
                names = {"--get"},
                required = true,
                description = "Get the current API token (masked by default)")
        boolean getToken;

        @CommandLine.Option(
                names = {"--unmask"},
                description = "Output the full unmasked token value")
        boolean unmask;
    }

    private final AuthCredentialStore credentialStore;
    private final TokenRefresher tokenRefresher;

    public AuthToken() {
        this(new AuthCredentialStore(), null);
    }

    public AuthToken(AuthCredentialStore credentialStore) {
        this(credentialStore, null);
    }

    AuthToken(AuthCredentialStore credentialStore, TokenRefresher tokenRefresher) {
        this.credentialStore = credentialStore;
        this.tokenRefresher = tokenRefresher;
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) {

        if (operation.setToken != null) {
            credentialStore.storeApiToken(operation.setToken);
            credentialStore.storeAuthMode("token");
            printer.printSuccessMessage("API token has been set");
            return EXIT_OK;
        }

        if (operation.getOptions != null) {
            String apiToken = getValidAccessToken(printer);
            if (StringHelper.isNotEmpty(apiToken)) {
                if (operation.getOptions.unmask) {
                    printer.printInfoMessage(apiToken);
                } else {
                    String maskedToken = maskToken(apiToken);
                    printer.printInfoMessage("Current API token: " + maskedToken);
                }
            } else {
                printer.printInfoMessage("No API token is currently set");
            }
            return EXIT_OK;
        }

        if (operation.clearToken) {
            String currentToken = credentialStore.getApiToken();
            if (currentToken != null) {
                credentialStore.storeApiToken("");
                printer.printSuccessMessage("API token has been cleared");
            } else {
                printer.printInfoMessage("No API token was set");
            }
        }
        return EXIT_OK;
    }

    /**
     * Gets a valid access token, refreshing it if it is expired or about to expire.
     *
     * @param printer the printer for warning messages
     * @return a valid access token, or null if unavailable
     */
    private String getValidAccessToken(WanakuPrinter printer) {
        String apiToken = credentialStore.getApiToken();
        if (StringHelper.isEmpty(apiToken)) {
            return null;
        }

        if (isTokenExpiredOrExpiring() && tryRefreshToken(printer)) {
            apiToken = credentialStore.getApiToken();
        }

        return apiToken;
    }

    /**
     * Checks if the stored token is expired or about to expire.
     *
     * @return true if the token needs to be refreshed
     */
    private boolean isTokenExpiredOrExpiring() {
        long expiryEpochSeconds = credentialStore.getTokenExpiry();
        if (expiryEpochSeconds == 0) {
            // No expiry stored - might be a token from before this feature
            return false;
        }

        long currentEpochSeconds = Instant.now().getEpochSecond();
        long secondsUntilExpiry = expiryEpochSeconds - currentEpochSeconds;

        return secondsUntilExpiry <= REFRESH_BUFFER_SECONDS;
    }

    /**
     * Attempts to refresh the access token using the stored refresh token.
     *
     * @param printer the printer for warning messages
     * @return true if refresh was successful, false otherwise
     */
    private boolean tryRefreshToken(WanakuPrinter printer) {
        String refreshToken = credentialStore.getRefreshToken();
        String authServerUrl = credentialStore.getAuthServerUrl();
        String clientId = credentialStore.getClientId();

        if (StringHelper.isEmpty(refreshToken)) {
            return false;
        }

        if (StringHelper.isEmpty(authServerUrl)) {
            return false;
        }

        if (StringHelper.isEmpty(clientId)) {
            clientId = "admin-cli";
        }

        String realm = credentialStore.getRealm();

        try {
            TokenRefresher refresher = tokenRefresher != null ? tokenRefresher : new TokenRefresher(insecure);
            RefreshResult result = refresher.refresh(refreshToken, authServerUrl, clientId, realm);

            credentialStore.storeApiToken(result.getAccessToken());
            credentialStore.storeRefreshToken(result.getRefreshToken());
            credentialStore.storeTokenExpiry(result.getExpiryEpochSeconds());

            return true;
        } catch (TokenRefresher.TokenRefreshException e) {
            printer.printWarningMessage("Token refresh failed, returning existing token: " + e.getMessage());
            return false;
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}
