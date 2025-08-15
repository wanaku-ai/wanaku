package ai.wanaku.cli.main.support;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.core.services.api.ToolsService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.util.List;

public class ToolHelper {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ToolHelper() {}

    /**
     * Imports a list of tool references to the specified host.
     * <p>
     * Creates a Quarkus REST client for the {@link ToolsService} at the given host
     * and adds each tool reference from the provided list to the service.
     * </p>
     *
     * @param toolReferences a list of tool references to import
     * @param host the base URI of the host where the ToolsService is deployed
     * @throws IllegalArgumentException if the host URI is invalid
     * @throws RuntimeException if there's an error connecting to or communicating with the service
     */
    public static void importToolset(List<ToolReference> toolReferences, String host) throws IOException {
        ToolsService toolsService =
                QuarkusRestClientBuilder.newBuilder().baseUri(URI.create(host)).build(ToolsService.class);

        for (var toolReference : toolReferences) {
            try {
                WanakuResponse<ToolReference> ignored = toolsService.add(toolReference);
            } catch (WebApplicationException ex) {
                Response response = ex.getResponse();
                if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                    System.err.printf(
                            "There is no downstream service capable of handling requests of the given type (%s): %s%n",
                            toolReference.getType(), response.getStatusInfo().getReasonPhrase());

                    System.exit(1);
                } else {
                    commonResponseErrorHandler(response);
                }
            }
        }
    }

    /**
     * Imports a single tool reference to the specified host.
     * <p>
     * Convenience method that wraps the single tool reference in a list
     * and delegates to {@link #importToolset(List, String)}.
     * </p>
     *
     * @param toolReference the tool reference to import
     * @param host the base URI of the host where the ToolsService is deployed
     * @throws IllegalArgumentException if the host URI is invalid
     * @throws RuntimeException if there's an error connecting to or communicating with the service
     * @see #importToolset(List, String)
     */
    public static void importToolset(ToolReference toolReference, String host) throws IOException {
        importToolset(List.of(toolReference), host);
    }
}
