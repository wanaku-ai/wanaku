package ai.wanaku.cli.main.commands.realm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.admin.BaseAdminCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import picocli.CommandLine;

@CommandLine.Command(name = "create", description = "Import a Keycloak realm from a configuration file")
public class RealmCreate extends BaseAdminCommand {

    private static final String DEFAULT_CONFIG = "deploy/auth/wanaku-config.json";

    @CommandLine.Option(
            names = {"--config"},
            description = "Path to the realm configuration JSON file",
            defaultValue = DEFAULT_CONFIG)
    private String config = DEFAULT_CONFIG;

    public RealmCreate() {
        super();
    }

    public RealmCreate(KeycloakAdminClient adminClient) {
        super(adminClient);
    }

    public RealmCreate(KeycloakAdminClient adminClient, String config) {
        super(adminClient);
        this.config = config;
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            String realmJson = Files.readString(Path.of(config));
            KeycloakAdminClient client = createAdminClient();
            client.importRealm(realmJson);
            printer.printSuccessMessage("Realm imported successfully from " + config);
            return EXIT_OK;
        } catch (IOException e) {
            printer.printErrorMessage("Failed to read configuration file '" + config + "': " + e.getMessage());
            return EXIT_ERROR;
        } catch (KeycloakAdminClient.KeycloakAdminException e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }
}
