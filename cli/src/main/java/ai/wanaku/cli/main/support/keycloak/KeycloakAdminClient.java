package ai.wanaku.cli.main.support.keycloak;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class KeycloakAdminClient {

    private final HttpClient httpClient;
    private final String keycloakUrl;
    private final String accessToken;
    private final ObjectMapper objectMapper;

    public KeycloakAdminClient(String keycloakUrl, String accessToken) {
        this(HttpClient.newHttpClient(), keycloakUrl, accessToken);
    }

    public KeycloakAdminClient(HttpClient httpClient, String keycloakUrl, String accessToken) {
        this.httpClient = httpClient;
        this.keycloakUrl = keycloakUrl.endsWith("/") ? keycloakUrl.substring(0, keycloakUrl.length() - 1) : keycloakUrl;
        this.accessToken = accessToken;
        this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ---- User methods ----

    public void createUser(String realm, String username, String password, String email) throws KeycloakAdminException {
        Map<String, Object> userRep = new java.util.LinkedHashMap<>();
        userRep.put("username", username);
        userRep.put("enabled", true);
        if (email != null && !email.isBlank()) {
            userRep.put("email", email);
        }
        if (password != null && !password.isBlank()) {
            Map<String, Object> credential = Map.of("type", "password", "value", password, "temporary", false);
            userRep.put("credentials", List.of(credential));
        }

        String url = keycloakUrl + "/admin/realms/" + realm + "/users";
        HttpRequest request = postRequest(url, userRep);
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 201) {
            throw new KeycloakAdminException("Failed to create user '" + username + "': " + response.body());
        }
    }

    public List<Map<String, Object>> listUsers(String realm) throws KeycloakAdminException {
        String url = keycloakUrl + "/admin/realms/" + realm + "/users";
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 200) {
            throw new KeycloakAdminException("Failed to list users: " + response.body());
        }

        return parseList(response.body());
    }

    public void deleteUser(String realm, String username) throws KeycloakAdminException {
        String userId = resolveUserId(realm, username);
        String url = keycloakUrl + "/admin/realms/" + realm + "/users/" + userId;
        HttpRequest request = deleteRequest(url);
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 204) {
            throw new KeycloakAdminException("Failed to delete user '" + username + "': " + response.body());
        }
    }

    public void setPassword(String realm, String username, String password) throws KeycloakAdminException {
        String userId = resolveUserId(realm, username);
        String url = keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/reset-password";
        Map<String, Object> credential = Map.of("type", "password", "value", password, "temporary", false);
        HttpRequest request = putRequest(url, credential);
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 204) {
            throw new KeycloakAdminException("Failed to set password for user '" + username + "': " + response.body());
        }
    }

    // ---- Client methods ----

    public void createClient(String realm, String clientId, String description) throws KeycloakAdminException {
        Map<String, Object> clientRep = new java.util.LinkedHashMap<>();
        clientRep.put("clientId", clientId);
        clientRep.put("enabled", true);
        clientRep.put("clientAuthenticatorType", "client-secret");
        clientRep.put("serviceAccountsEnabled", true);
        clientRep.put("publicClient", false);
        if (description != null && !description.isBlank()) {
            clientRep.put("description", description);
        }

        String url = keycloakUrl + "/admin/realms/" + realm + "/clients";
        HttpRequest request = postRequest(url, clientRep);
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 201) {
            throw new KeycloakAdminException("Failed to create client '" + clientId + "': " + response.body());
        }
    }

    public List<Map<String, Object>> listClients(String realm) throws KeycloakAdminException {
        String url = keycloakUrl + "/admin/realms/" + realm + "/clients";
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 200) {
            throw new KeycloakAdminException("Failed to list clients: " + response.body());
        }

        return parseList(response.body());
    }

    public void deleteClient(String realm, String clientId) throws KeycloakAdminException {
        String internalId = resolveClientInternalId(realm, clientId);
        String url = keycloakUrl + "/admin/realms/" + realm + "/clients/" + internalId;
        HttpRequest request = deleteRequest(url);
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 204) {
            throw new KeycloakAdminException("Failed to delete client '" + clientId + "': " + response.body());
        }
    }

    public String regenerateClientSecret(String realm, String clientId) throws KeycloakAdminException {
        String internalId = resolveClientInternalId(realm, clientId);
        String url = keycloakUrl + "/admin/realms/" + realm + "/clients/" + internalId + "/client-secret";
        HttpRequest request = postRequest(url, Map.of());
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 200) {
            throw new KeycloakAdminException(
                    "Failed to regenerate secret for client '" + clientId + "': " + response.body());
        }

        return extractSecret(response.body());
    }

    public String getClientSecret(String realm, String clientId) throws KeycloakAdminException {
        String internalId = resolveClientInternalId(realm, clientId);
        String url = keycloakUrl + "/admin/realms/" + realm + "/clients/" + internalId + "/client-secret";
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 200) {
            throw new KeycloakAdminException("Failed to get secret for client '" + clientId + "': " + response.body());
        }

        return extractSecret(response.body());
    }

    // ---- Realm methods ----

    public void importRealm(String realmJson) throws KeycloakAdminException {
        String url = keycloakUrl + "/admin/realms";
        HttpRequest request = postRawJsonRequest(url, realmJson);
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 201) {
            throw new KeycloakAdminException(
                    "Failed to import realm (HTTP " + response.statusCode() + "): " + response.body());
        }
    }

    // ---- Private helpers ----

    private String resolveUserId(String realm, String username) throws KeycloakAdminException {
        String url = keycloakUrl + "/admin/realms/" + realm + "/users?username=" + username + "&exact=true";
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 200) {
            throw new KeycloakAdminException("Failed to resolve user '" + username + "': " + response.body());
        }

        List<Map<String, Object>> users = parseList(response.body());
        if (users.isEmpty()) {
            throw new KeycloakAdminException("User '" + username + "' not found");
        }

        return (String) users.get(0).get("id");
    }

    private String resolveClientInternalId(String realm, String clientId) throws KeycloakAdminException {
        String url = keycloakUrl + "/admin/realms/" + realm + "/clients?clientId=" + clientId;
        HttpRequest request = getRequest(url);
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 200) {
            throw new KeycloakAdminException("Failed to resolve client '" + clientId + "': " + response.body());
        }

        List<Map<String, Object>> clients = parseList(response.body());
        if (clients.isEmpty()) {
            throw new KeycloakAdminException("Client '" + clientId + "' not found");
        }

        return (String) clients.get(0).get("id");
    }

    private String extractSecret(String body) throws KeycloakAdminException {
        try {
            Map<String, Object> map = objectMapper.readValue(body, new TypeReference<>() {});
            Object value = map.get("value");
            return value != null ? value.toString() : null;
        } catch (IOException e) {
            throw new KeycloakAdminException("Failed to parse client secret response", e);
        }
    }

    private HttpRequest getRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private HttpRequest postRequest(String url, Object body) throws KeycloakAdminException {
        try {
            String json = objectMapper.writeValueAsString(body);
            return HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
        } catch (IOException e) {
            throw new KeycloakAdminException("Failed to serialize request body", e);
        }
    }

    private HttpRequest postRawJsonRequest(String url, String json) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
    }

    private HttpRequest putRequest(String url, Object body) throws KeycloakAdminException {
        try {
            String json = objectMapper.writeValueAsString(body);
            return HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
        } catch (IOException e) {
            throw new KeycloakAdminException("Failed to serialize request body", e);
        }
    }

    private HttpRequest deleteRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .DELETE()
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) throws KeycloakAdminException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KeycloakAdminException("Failed to communicate with Keycloak at " + keycloakUrl, e);
        }
    }

    private List<Map<String, Object>> parseList(String json) throws KeycloakAdminException {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (IOException e) {
            throw new KeycloakAdminException("Failed to parse Keycloak response", e);
        }
    }

    public static class KeycloakAdminException extends Exception {
        public KeycloakAdminException(String message) {
            super(message);
        }

        public KeycloakAdminException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
