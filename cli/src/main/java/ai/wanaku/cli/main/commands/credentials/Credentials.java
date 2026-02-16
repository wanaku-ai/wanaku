package ai.wanaku.cli.main.commands.credentials;

import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "credentials",
        description = "Keycloak service client credential management commands",
        subcommands = {
            CredentialsAdd.class,
            CredentialsList.class,
            CredentialsRemove.class,
            CredentialsRegenerate.class,
            CredentialsShow.class
        })
public class Credentials extends BaseCommand {

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        CommandLine.usage(this, System.out);
        return EXIT_ERROR;
    }
}
