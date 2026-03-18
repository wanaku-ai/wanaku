package ai.wanaku.cli.main.commands.admin;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.keycloak.KeycloakAdminClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

public abstract class BaseAdminCommand extends BaseCommand {

    @FunctionalInterface
    protected interface EnvironmentProvider {
        String get(String name);
    }

    private static final String DEFAULT_KEYCLOAK_URL = "http://localhost:8543";
    private static final String DEFAULT_REALM = "wanaku";
    private static final String ENV_ADMIN_USERNAME = "WANAKU_ADMIN_USERNAME";
    private static final String ENV_ADMIN_PASSWORD = "WANAKU_ADMIN_PASSWORD";

    @Spec
    protected CommandSpec spec;

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

    @CommandLine.Option(
            names = {"--admin-username"},
            description = "Admin username for Keycloak (or set " + ENV_ADMIN_USERNAME + ")")
    protected String adminUsername;

    @CommandLine.Option(
            names = {"--admin-password"},
            description = "Admin password for Keycloak (or set " + ENV_ADMIN_PASSWORD + ")",
            interactive = true,
            arity = "0..1")
    protected String adminPassword;

    private final KeycloakAdminClient adminClientOverride;
    private final EnvironmentProvider environmentProvider;

    protected BaseAdminCommand() {
        this(null, System::getenv);
    }

    protected BaseAdminCommand(KeycloakAdminClient adminClientOverride) {
        this(adminClientOverride, System::getenv);
    }

    protected BaseAdminCommand(KeycloakAdminClient adminClientOverride, EnvironmentProvider environmentProvider) {
        this.adminClientOverride = adminClientOverride;
        this.environmentProvider = environmentProvider == null ? System::getenv : environmentProvider;
    }

    protected KeycloakAdminClient createAdminClient() {
        if (adminClientOverride != null) {
            return adminClientOverride;
        }

        String token = obtainAdminToken();
        return new KeycloakAdminClient(keycloakUrl, token);
    }

    private String obtainAdminToken() {
        String tokenUrl = keycloakUrl + "/realms/master/protocol/openid-connect/token";
        String resolvedUsername = resolveAdminUsername();
        String resolvedPassword = resolveAdminPassword();

        String formBody = "grant_type=password"
                + "&client_id=admin-cli"
                + "&username=" + URLEncoder.encode(resolvedUsername, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(resolvedPassword, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Failed to obtain admin token (HTTP " + response.statusCode() + "): " + response.body());
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> tokenResponse = mapper.readValue(response.body(), new TypeReference<>() {});
            Object accessToken = tokenResponse.get("access_token");
            if (accessToken == null) {
                throw new IllegalStateException("No access_token in Keycloak response");
            }
            return accessToken.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to communicate with Keycloak at " + keycloakUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while obtaining admin token", e);
        }
    }

    protected String resolveAdminUsername() {
        if (adminUsername != null && !adminUsername.isBlank()) {
            return adminUsername;
        }
        String envValue = environmentProvider.get(ENV_ADMIN_USERNAME);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        throw missingParameter("Admin username not provided. Use --admin-username or set " + ENV_ADMIN_USERNAME + ".");
    }

    protected String resolveAdminPassword() {
        if (adminPassword != null && !adminPassword.isBlank()) {
            return adminPassword;
        }
        String envValue = environmentProvider.get(ENV_ADMIN_PASSWORD);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        throw missingParameter("Admin password not provided. Use --admin-password or set " + ENV_ADMIN_PASSWORD + ".");
    }

    private CommandLine.ParameterException missingParameter(String message) {
        CommandLine commandLine = spec == null ? new CommandLine(this) : spec.commandLine();
        return new CommandLine.ParameterException(commandLine, message);
    }
}
