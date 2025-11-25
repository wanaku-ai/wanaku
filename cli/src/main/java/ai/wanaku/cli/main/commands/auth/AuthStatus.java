package ai.wanaku.cli.main.commands.auth;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(name = "status", description = "Show current authentication status")
public class AuthStatus extends BaseCommand {

    private AuthCredentialStore credentialStore;

    public AuthStatus() {
        this(new AuthCredentialStore());
    }

    public AuthStatus(AuthCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {

        printer.printInfoMessage("Authentication Status:");
        printer.printInfoMessage("=====================");

        String authMode = credentialStore.getAuthMode();
        printer.printInfoMessage("Mode: " + authMode);

        String apiToken = credentialStore.getApiToken();
        if (apiToken != null) {
            String maskedToken = maskToken(apiToken);
            printer.printInfoMessage("API Token: " + maskedToken);
        } else {
            printer.printInfoMessage("API Token: Not set");
        }

        String authServerUrl = credentialStore.getAuthServerUrl();
        if (authServerUrl != null) {
            printer.printInfoMessage("Auth Server: " + authServerUrl);
        }

        String refreshToken = credentialStore.getRefreshToken();
        if (refreshToken != null) {
            String maskedRefreshToken = maskToken(refreshToken);
            printer.printInfoMessage("Refresh Token: " + maskedRefreshToken);
        }

        printer.printInfoMessage("Credentials File: " + java.nio.file.Paths.get(credentialStore.getCredentialsFile()));
        printer.printInfoMessage("Has Credentials: " + credentialStore.hasCredentials());

        return EXIT_OK;
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}
