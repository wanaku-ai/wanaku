package ai.wanaku.cli.main.support;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.cli.main.services.ToolsService;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

import java.net.URI;
import java.util.List;

public class ToolHelper {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ToolHelper(){}


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
    public static void importToolset (List<ToolReference> toolReferences, String host) {
        ToolsService toolsService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(ToolsService.class);

        for (var toolReference : toolReferences) {
            toolsService.add(toolReference);
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
    public static void importToolset (ToolReference toolReference, String host) {
        importToolset(List.of(toolReference), host);
    }

}
