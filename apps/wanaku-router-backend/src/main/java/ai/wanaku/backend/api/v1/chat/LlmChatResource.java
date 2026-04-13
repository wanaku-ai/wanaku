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

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;

@ApplicationScoped
@Path("/api/v1/chat")
public class LlmChatResource {

    /* Allowlist of chat URLs */
    private final List<String> allowlist = List.of(
            "http://localhost:11434",
            "https://api.openai.com",
            "https://api.mistral.ai",
            "https://generativelanguage.googleapis.com/v1beta/openai/",
            "https://api.anthropic.com");

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
        String apiKey = json.getString("apiKey");
        JsonObject llmParams = json.getJsonObject("chatParams");

        if (!allowlist.contains(baseUrl)) {
            throw new WebApplicationException("Base URL: " + baseUrl + " is not allowed", BAD_REQUEST);
        }
        try (var client = HttpClient.newHttpClient()) {
            String url = baseUrl + "/v1/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .POST(BodyPublishers.ofString(llmParams.toString()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .build();
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new WebApplicationException(TOO_MANY_REQUESTS);
            }
            if (response.statusCode() != 200) {
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
