package ai.wanaku.cli.main.commands.credentials;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jline.terminal.Terminal;
import io.quarkus.runtime.annotations.RegisterForReflection;
import ai.wanaku.cli.main.commands.admin.BaseAdminCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "List service clients")
public class CredentialsList extends BaseAdminCommand {

    private static final Set<String> INTERNAL_CLIENTS =
            Set.of("account", "account-console", "admin-cli", "broker", "realm-management", "security-admin-console");

    @RegisterForReflection
    public record ClientDisplay(String clientId, String description, boolean enabled) {}

    public CredentialsList() {
        super();
    }

    public CredentialsList(AuthCredentialStore credentialStore) {
        super(credentialStore);
    }

    public CredentialsList(AuthCredentialStore credentialStore, KeycloakAdminClient adminClient) {
        super(credentialStore, adminClient);
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            KeycloakAdminClient client = createAdminClient();
            List<Map<String, Object>> clients = client.listClients(realm);

            List<ClientDisplay> displayList = clients.stream()
                    .filter(c -> !INTERNAL_CLIENTS.contains(stringVal(c.get("clientId"))))
                    .map(c -> new ClientDisplay(
                            stringVal(c.get("clientId")), stringVal(c.get("description")), boolVal(c.get("enabled"))))
                    .toList();

            printer.printTable(displayList, "clientId", "description", "enabled");
            return EXIT_OK;
        } catch (KeycloakAdminClient.KeycloakAdminException e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }

    private static String stringVal(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private static boolean boolVal(Object obj) {
        return obj instanceof Boolean b && b;
    }
}
