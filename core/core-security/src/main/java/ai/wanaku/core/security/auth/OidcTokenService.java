package ai.wanaku.core.security.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for retrieving OAuth2/OIDC tokens from an authentication server.
 * Supports password grant type authentication.
 */
public class OidcTokenService {

    private static final String TOKEN_ENDPOINT_PATH = "/realms/%s/protocol/openid-connect/token";
    private static final String GRANT_TYPE_PASSWORD = "password";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OidcTokenService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public OidcTokenService(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Retrieves an access token using the password grant type.
     *
     * @param serverUrl the authentication server URL (e.g., "http://localhost:8543")
     * @param realm the authentication realm
     * @param clientId the OAuth2 client ID
     * @param username the username
     * @param password the password
     * @return the token response containing access token and optionally refresh token
     * @throws OidcTokenException if token retrieval fails
     */
    public TokenResponse retrieveToken(
            String serverUrl, String realm, String clientId, String username, String password)
            throws OidcTokenException {
        try {
            String tokenUrl = buildTokenUrl(serverUrl, realm);
            String formData = buildPasswordGrantFormData(clientId, username, password);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new OidcTokenException("Failed to retrieve token. Status: " + response.statusCode()
                        + ", Response: " + response.body());
            }

            return parseTokenResponse(response.body());
        } catch (OidcTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new OidcTokenException("Failed to retrieve token: " + e.getMessage(), e);
        }
    }

    /**
     * Refreshes an access token using a refresh token.
     *
     * @param serverUrl the authentication server URL
     * @param realm the authentication realm
     * @param clientId the OAuth2 client ID
     * @param refreshToken the refresh token
     * @return the token response with new access token
     * @throws OidcTokenException if token refresh fails
     */
    public TokenResponse refreshToken(String serverUrl, String realm, String clientId, String refreshToken)
            throws OidcTokenException {
        try {
            String tokenUrl = buildTokenUrl(serverUrl, realm);

            Map<String, String> parameters = new HashMap<>();
            parameters.put("client_id", clientId);
            parameters.put("grant_type", "refresh_token");
            parameters.put("refresh_token", refreshToken);

            String formData = encodeFormData(parameters);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new OidcTokenException(
                        "Failed to refresh token. Status: " + response.statusCode() + ", Response: " + response.body());
            }

            return parseTokenResponse(response.body());
        } catch (OidcTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new OidcTokenException("Failed to refresh token: " + e.getMessage(), e);
        }
    }

    private String buildTokenUrl(String serverUrl, String realm) {
        return String.format(serverUrl + TOKEN_ENDPOINT_PATH, realm);
    }

    private String buildPasswordGrantFormData(String clientId, String username, String password) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("client_id", clientId);
        parameters.put("username", username);
        parameters.put("password", password);
        parameters.put("grant_type", GRANT_TYPE_PASSWORD);

        return encodeFormData(parameters);
    }

    private String encodeFormData(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private TokenResponse parseTokenResponse(String responseBody) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        String accessToken = jsonNode.get("access_token").asText();
        String refreshToken =
                jsonNode.has("refresh_token") ? jsonNode.get("refresh_token").asText() : null;
        Long expiresIn = jsonNode.has("expires_in") ? jsonNode.get("expires_in").asLong() : null;
        String tokenType =
                jsonNode.has("token_type") ? jsonNode.get("token_type").asText() : "Bearer";

        return new TokenResponse(accessToken, refreshToken, expiresIn, tokenType);
    }
}
