package ai.wanaku.cli.main.commands.users;

import java.util.List;
import java.util.Map;
import org.jline.terminal.Terminal;
import io.quarkus.runtime.annotations.RegisterForReflection;
import ai.wanaku.cli.main.commands.admin.BaseAdminCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import picocli.CommandLine;

@CommandLine.Command(name = "list", description = "List Keycloak users")
public class UsersList extends BaseAdminCommand {

    @RegisterForReflection
    public record UserDisplay(String username, String email, boolean enabled) {}

    public UsersList() {
        super();
    }

    public UsersList(KeycloakAdminClient adminClient) {
        super(adminClient);
    }

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        try {
            KeycloakAdminClient client = createAdminClient();
            List<Map<String, Object>> users = client.listUsers(realm);

            List<UserDisplay> displayList = users.stream()
                    .map(u -> new UserDisplay(
                            stringVal(u.get("username")), stringVal(u.get("email")), boolVal(u.get("enabled"))))
                    .toList();

            printer.printTable(displayList, "username", "email", "enabled");
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
