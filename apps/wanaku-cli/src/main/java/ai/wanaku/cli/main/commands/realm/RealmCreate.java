package ai.wanaku.cli.main.commands.realm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.admin.BaseAdminCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import picocli.CommandLine;

@CommandLine.Command(name = "create", description = "Import a Keycloak realm from a configuration file")
public class RealmCreate extends BaseAdminCommand {

    private static final String DEFAULT_CONFIG = "deploy/auth/wanaku-config.json";

    /**
     * Matches Keycloak-style environment variable placeholders with a default value,
     * e.g. {@code ${WANAKU_SERVICE_SECRET:mypasswd}}. Restricted to uppercase variable
     * names so Keycloak i18n keys such as {@code ${role_admin}} are left untouched.
     */
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*):([^}]*)\\}");

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
    public Integer doCall(Terminal terminal, WanakuPrinter printer) {
        try {
            String realmJson = resolveEnvPlaceholders(Files.readString(Path.of(config)), System::getenv);
            KeycloakAdminClient client = createAdminClient();
            client.importRealm(realmJson);
            String serviceClientSecret = client.getClientSecret("wanaku", "wanaku-service");
            printer.printSuccessMessage("Realm imported successfully from " + config);
            if (serviceClientSecret != null) {
                printer.printInfoMessage("wanaku-service client secret: " + serviceClientSecret);
            }
            return EXIT_OK;
        } catch (IOException e) {
            printer.printErrorMessage("Failed to read configuration file '" + config + "': " + e.getMessage());
            return EXIT_ERROR;
        } catch (KeycloakAdminClient.KeycloakAdminException e) {
            printer.printErrorMessage(e.getMessage());
            return EXIT_ERROR;
        }
    }

    /**
     * Resolves {@code ${VAR:default}} placeholders in the realm JSON before import.
     * Keycloak only substitutes environment variables when importing at startup
     * ({@code --import-realm}); imports through the Admin REST API store the
     * placeholder as a literal value, leaving clients with a secret that never
     * matches what services expect.
     */
    static String resolveEnvPlaceholders(String json, UnaryOperator<String> env) {
        Matcher matcher = ENV_PLACEHOLDER.matcher(json);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = env.apply(matcher.group(1));
            if (value == null || value.isBlank()) {
                value = matcher.group(2);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(escapeJson(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
