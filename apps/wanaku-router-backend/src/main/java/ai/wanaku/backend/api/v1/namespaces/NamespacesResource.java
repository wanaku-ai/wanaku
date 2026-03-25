package ai.wanaku.backend.api.v1.namespaces;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.exceptions.NamespaceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

@ApplicationScoped
@Path("/api/v1/namespaces")
public class NamespacesResource {
    private static final long DEFAULT_MAX_AGE_SECONDS = 604800; // 7 days

    @Inject
    NamespacesBean namespacesBean;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<Namespace>> list(@QueryParam("labelFilter") String labelFilter) {
        List<Namespace> namespaces = namespacesBean.list(labelFilter);
        return new WanakuResponse<>(namespaces);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<Namespace> create(Namespace namespace) {
        Namespace created = namespacesBean.create(namespace);
        return new WanakuResponse<>(created);
    }

    /**
     * Retrieves a specific namespace by its ID.
     *
     * @param id the ID of the namespace to retrieve
     * @return a {@link WanakuResponse} containing the namespace details
     */
    @Path("/{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<Namespace> getById(@PathParam("id") String id) {
        Namespace namespace = namespacesBean.getById(id);
        if (namespace == null) {
            throw NamespaceNotFoundException.forId(id);
        }
        return new WanakuResponse<>(namespace);
    }

    /**
     * Updates an existing namespace.
     *
     * @param namespace the namespace object with updated information
     * @return a {@link Response} indicating the result of the update operation
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") String id, Namespace namespace) {
        if (!namespacesBean.exists(id)) {
            throw NamespaceNotFoundException.forId(id);
        }
        if (namespacesBean.update(id, namespace)) {
            return Response.ok().build();
        }
        throw NamespaceNotFoundException.forId(id);
    }

    @Path("/{id}")
    @DELETE
    public Response delete(@PathParam("id") String id) {
        if (namespacesBean.deleteById(id)) {
            return Response.ok().build();
        }
        throw NamespaceNotFoundException.forId(id);
    }

    @Path("/stale")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<Namespace>> listStale(
            @QueryParam("maxAgeSeconds") Long maxAgeSeconds,
            @QueryParam("unassignedOnly") Boolean unassignedOnly,
            @QueryParam("includeUnlabeled") Boolean includeUnlabeled) {
        long ageSeconds = maxAgeSeconds != null ? maxAgeSeconds : DEFAULT_MAX_AGE_SECONDS;
        boolean onlyUnassigned = unassignedOnly == null || unassignedOnly;
        boolean includeMissingLabels = includeUnlabeled != null && includeUnlabeled;

        List<Namespace> staleNamespaces = namespacesBean.listStale(ageSeconds, onlyUnassigned, includeMissingLabels);
        return new WanakuResponse<>(staleNamespaces);
    }

    @Path("/stale")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<Integer> cleanupStale(
            @QueryParam("maxAgeSeconds") Long maxAgeSeconds,
            @QueryParam("unassignedOnly") Boolean unassignedOnly,
            @QueryParam("includeUnlabeled") Boolean includeUnlabeled) {
        long ageSeconds = maxAgeSeconds != null ? maxAgeSeconds : DEFAULT_MAX_AGE_SECONDS;
        boolean onlyUnassigned = unassignedOnly == null || unassignedOnly;
        boolean includeMissingLabels = includeUnlabeled != null && includeUnlabeled;

        int removed = namespacesBean.cleanupStale(ageSeconds, onlyUnassigned, includeMissingLabels);
        return new WanakuResponse<>(removed);
    }
}
