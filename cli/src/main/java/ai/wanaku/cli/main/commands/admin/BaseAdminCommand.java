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

    @CommandLine.Option(
            names = {"--admin-username"},
            description = "Admin username for Keycloak",
            required = true)
    protected String adminUsername;

    @CommandLine.Option(
            names = {"--admin-password"},
            description = "Admin password for Keycloak",
            required = true,
            interactive = true,
            arity = "0..1")
    protected String adminPassword;

    private final KeycloakAdminClient adminClientOverride;

    protected BaseAdminCommand() {
        this(null);
    }

    protected BaseAdminCommand(KeycloakAdminClient adminClientOverride) {
        this.adminClientOverride = adminClientOverride;
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

        String formBody = "grant_type=password"
                + "&client_id=admin-cli"
                + "&username=" + URLEncoder.encode(adminUsername, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(adminPassword, StandardCharsets.UTF_8);

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
}
