package ai.wanaku.backend.api.v1.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;

@ApplicationScoped
@Path("/api/v1/chat")
public class LlmChatResource {
    private static final Logger LOG = Logger.getLogger(LlmChatResource.class);

    @ConfigProperty(name = "wanaku.chat.allowlist")
    List<String> allowlist;

    @GET
    @Path("/allowlist")
    @Produces(APPLICATION_JSON)
    public List<String> getBaseUrls() {
        return allowlist;
    }

    @POST
    @Path("/completions")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public String codeCompletions(String data) {
        JsonObject json = Json.createReader(new StringReader(data)).readObject();
        String baseUrl = json.getString("baseUrl");
        String apiKey = json.getString("apiKey", null);
        JsonObject llmParams = json.getJsonObject("chatParams");

        if (!allowlist.contains(baseUrl)) {
            throw new WebApplicationException("Base URL: %s is not allowed".formatted(baseUrl), BAD_REQUEST);
        }
        try (var client = HttpClient.newHttpClient()) {
            String url = baseUrl + "/v1/chat/completions";
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .POST(BodyPublishers.ofString(llmParams.toString()))
                    .header("Content-Type", APPLICATION_JSON);

            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            if (response.statusCode() != OK.getStatusCode()) {
                LOG.errorf("HTTP Response Error: %s", response.body());

                if (response.statusCode() == TOO_MANY_REQUESTS.getStatusCode()) {
                    throw new WebApplicationException(TOO_MANY_REQUESTS);
                }

                throw new WebApplicationException(INTERNAL_SERVER_ERROR);
            }

            return response.body();
        } catch (URISyntaxException ex) {
            throw new WebApplicationException("Malformed base URL", ex, BAD_REQUEST);
        } catch (IOException | InterruptedException ex) {
            throw new WebApplicationException(INTERNAL_SERVER_ERROR);
        }
    }
}
