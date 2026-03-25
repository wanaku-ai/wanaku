package ai.wanaku.cli.main.commands.auth;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "logout", description = "Clear stored authentication credentials")
public class AuthLogout extends BaseCommand {

    private AuthCredentialStore credentialStore;

    public AuthLogout() {
        this(new AuthCredentialStore());
    }

    public AuthLogout(AuthCredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {

        if (!credentialStore.hasCredentials()) {
            printer.printInfoMessage("No authentication credentials found");
            return EXIT_OK;
        }

        credentialStore.clearCredentials();
        printer.printSuccessMessage("Successfully cleared authentication credentials");

        return EXIT_OK;
    }
}
