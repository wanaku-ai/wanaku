package ai.wanaku.cli.main.commands.realm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * Matches property placeholders with a default value, e.g.
     * {@code ${VAR_NAME:default}}. Placeholders without a colon (such as
     * Keycloak's localization keys like {@code ${client_account}}) are left
     * untouched.
     */
    static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:]+):([^}]*)\\}");

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
            String realmJson = Files.readString(Path.of(config));
            realmJson = resolvePropertyPlaceholders(realmJson);
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

    /**
     * Resolves {@code ${VAR:default}} property placeholders in the realm JSON
     * by looking up the named environment variable, falling back to the default
     * value when the variable is not set or blank.
     *
     * <p>Keycloak's startup-based realm import ({@code --import-realm})
     * resolves these automatically, but the Admin REST API does not. This
     * method provides equivalent behavior for REST API imports so that the
     * imported client secrets match what downstream services expect.</p>
     *
     * <p>Placeholders without a default (e.g. {@code ${client_account}}) are
     * Keycloak localization keys and are intentionally left untouched.</p>
     *
     * @param json the raw realm configuration JSON
     * @return the JSON with environment-variable placeholders resolved
     */
    static String resolvePropertyPlaceholders(String json) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(json);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String envVar = matcher.group(1);
            String defaultValue = matcher.group(2);
            String envValue = System.getenv(envVar);
            String resolved = (envValue != null && !envValue.isBlank()) ? envValue : defaultValue;
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
