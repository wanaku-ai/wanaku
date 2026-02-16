package ai.wanaku.cli.main.commands.admin;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.AuthCredentialStore;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import picocli.CommandLine;

public abstract class BaseAdminCommand extends BaseCommand {

    private static final String DEFAULT_KEYCLOAK_URL = "http://localhost:8543";
    private static final String DEFAULT_REALM = "wanaku";

    @CommandLine.Option(
            names = {"--keycloak-url"},
            description = "Keycloak admin URL",
            defaultValue = DEFAULT_KEYCLOAK_URL)
    protected String keycloakUrl;

    @CommandLine.Option(
            names = {"--realm"},
            description = "Keycloak realm",
            defaultValue = DEFAULT_REALM)
    protected String realm;

    protected final AuthCredentialStore credentialStore;
    private final KeycloakAdminClient adminClientOverride;

    protected BaseAdminCommand() {
        this(new AuthCredentialStore());
    }

    protected BaseAdminCommand(AuthCredentialStore credentialStore) {
        this(credentialStore, null);
    }

    protected BaseAdminCommand(AuthCredentialStore credentialStore, KeycloakAdminClient adminClientOverride) {
        this.credentialStore = credentialStore;
        this.adminClientOverride = adminClientOverride;
    }

    protected KeycloakAdminClient createAdminClient() {
        if (adminClientOverride != null) {
            return adminClientOverride;
        }
        String token = credentialStore.getApiToken();
        if (token == null) {
            throw new IllegalStateException("No authentication token found. Please run 'wanaku auth login' first.");
        }
        return new KeycloakAdminClient(keycloakUrl, token);
    }
}
