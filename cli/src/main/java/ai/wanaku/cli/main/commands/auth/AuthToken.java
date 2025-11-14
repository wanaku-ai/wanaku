package ai.wanaku.cli.main.commands.auth;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

@CommandLine.Command(name = "token", description = "Manage API tokens")
public class AuthToken extends BaseCommand {

    @CommandLine.Option(
            names = {"--set"},
            description = "Set the API token")
    private String setToken;

    @CommandLine.Option(
            names = {"--get"},
            description = "Get the current API token (masked)")
    private boolean getToken;

    @CommandLine.Option(
            names = {"--clear"},
            description = "Clear the API token")
    private boolean clearToken;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        AuthCredentialStore credentialStore = new AuthCredentialStore();

        if (setToken != null) {
            credentialStore.storeApiToken(setToken);
            credentialStore.storeAuthMode("token");
            printer.printSuccessMessage("API token has been set");
            return EXIT_OK;
        }

        if (getToken) {
            String apiToken = credentialStore.getApiToken();
            if (apiToken != null) {
                String maskedToken = maskToken(apiToken);
                printer.printInfoMessage("Current API token: " + maskedToken);
            } else {
                printer.printInfoMessage("No API token is currently set");
            }
            return EXIT_OK;
        }

        if (clearToken) {
            String currentToken = credentialStore.getApiToken();
            if (currentToken != null) {
                credentialStore.storeApiToken("");
                printer.printSuccessMessage("API token has been cleared");
            } else {
                printer.printInfoMessage("No API token was set");
            }
            return EXIT_OK;
        }

        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}
