package ai.wanaku.cli.main.commands.auth;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "token", description = "Manage API tokens")
public class AuthToken extends BaseCommand {

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

    public AuthToken() {
        this(new AuthCredentialStore());
    }

    public AuthToken(AuthCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
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
            String apiToken = credentialStore.getApiToken();
            if (apiToken != null) {
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

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}
