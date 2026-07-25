package ai.wanaku.cli.main.support;

import jakarta.ws.rs.WebApplicationException;

import java.util.List;
import java.util.stream.Collectors;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.NamespacesService;
import picocli.CommandLine.Option;

import static ai.wanaku.cli.main.support.ResponseHelper.commonResponseErrorHandler;

/**
 * Shared Picocli argument group for namespace identification.
 * <p>
 * Provides two mutually exclusive ways to identify a namespace:
 * </p>
 * <ul>
 *   <li>{@code --namespace} ({@code -N}): a human-readable namespace name (common case)</li>
 *   <li>{@code --namespace-id}: a namespace UUID (scripting/advanced use)</li>
 * </ul>
 * <p>
 * When {@code --namespace-id} is provided, it is returned directly.
 * When {@code --namespace} is provided, the name is either passed through as-is
 * (for commands where the server resolves names) or resolved to a UUID via
 * {@link #resolveNamespaceId(NamespacesService)}.
 * </p>
 */
public class NamespaceOptions {

    private static final String ERROR_NO_NAMESPACE_MATCH =
            "No namespace matched '%s'. Use 'wanaku namespace list' to see available namespaces.";

    @Option(
            names = {"-N", "--namespace", "--namespace-name"},
            description = "The namespace name associated with this entity")
    String namespace;

    @Option(
            names = {"--namespace-id"},
            description = "The namespace UUID (alternative to --namespace)")
    String namespaceId;

    /**
     * Returns the namespace name, if provided via {@code --namespace}.
     *
     * @return the namespace name, or {@code null} if {@code --namespace-id} was used instead
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the namespace UUID, if provided via {@code --namespace-id}.
     *
     * @return the namespace UUID, or {@code null} if {@code --namespace} was used instead
     */
    public String getNamespaceId() {
        return namespaceId;
    }

    /**
     * Returns the namespace value to pass to the server.
     * <p>
     * If {@code --namespace-id} was provided, returns the UUID directly.
     * Otherwise returns the namespace name (the server resolves names for most commands).
     * </p>
     *
     * @return the namespace identifier (name or UUID)
     */
    public String getNamespaceValue() {
        if (namespaceId != null) {
            return namespaceId;
        }
        return namespace;
    }

    /**
     * Resolves the namespace to its UUID.
     * <p>
     * If {@code --namespace-id} was provided, returns it directly.
     * If {@code --namespace} was provided, queries the server to look up the UUID by name.
     * </p>
     *
     * @param namespacesService the service to use for looking up namespace by name
     * @return the namespace UUID
     * @throws Exception if the lookup fails or no matching namespace is found
     */
    public String resolveNamespaceId(NamespacesService namespacesService) throws Exception {
        if (namespaceId != null) {
            return namespaceId;
        }

        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException(
                    "Either --namespace or --namespace-id must be provided with a non-blank value.");
        }

        try {
            WanakuResponse<List<Namespace>> response = namespacesService.list();
            List<Namespace> data = response.data();
            if (data == null) {
                throw new IllegalStateException("No namespace data returned from server.");
            }

            List<Namespace> matches =
                    data.stream().filter(n -> namespace.equals(n.getName())).collect(Collectors.toList());

            if (matches.isEmpty()) {
                throw new IllegalArgumentException(ERROR_NO_NAMESPACE_MATCH.formatted(namespace));
            }
            if (matches.size() > 1) {
                throw new IllegalStateException(
                        "Multiple namespaces matched '%s'. Use --namespace-id with the UUID instead."
                                .formatted(namespace));
            }
            return matches.getFirst().getId();
        } catch (WebApplicationException ex) {
            commonResponseErrorHandler(ex.getResponse());
            throw ex;
        }
    }

    /**
     * Resolves the namespace to its human-readable name.
     * <p>
     * If {@code --namespace} was provided, returns the name directly.
     * If {@code --namespace-id} was provided, queries the server to look up the name by UUID.
     * </p>
     * <p>
     * This method should be used by commands whose backends resolve namespaces by name
     * (e.g., tools, prompts, resources) rather than by ID (e.g., forwards).
     * </p>
     *
     * @param namespacesService the service to use for looking up namespace by ID
     * @return the namespace name
     * @throws Exception if the lookup fails or no matching namespace is found
     */
    public String resolveNamespaceName(NamespacesService namespacesService) throws Exception {
        if (namespace != null) {
            return namespace;
        }

        if (namespaceId == null || namespaceId.isBlank()) {
            throw new IllegalArgumentException(
                    "Either --namespace or --namespace-id must be provided with a non-blank value.");
        }

        try {
            WanakuResponse<Namespace> response = namespacesService.getById(namespaceId);
            Namespace ns = response.data();
            if (ns == null) {
                throw new IllegalArgumentException(
                        "No namespace found with ID '%s'. Use 'wanaku namespaces list' to see available namespaces."
                                .formatted(namespaceId));
            }

            String name = ns.getName();
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                        "Namespace with ID '%s' has no name assigned. Use --namespace with a name instead, or assign a name to the namespace first."
                                .formatted(namespaceId));
            }
            return name;
        } catch (WebApplicationException ex) {
            commonResponseErrorHandler(ex.getResponse());
            throw ex;
        }
    }
}
