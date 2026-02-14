package ai.wanaku.backend.api.v1.namespaces;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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

    @Inject
    NamespacesBean namespacesBean;

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<Namespace>> list(@QueryParam("labelFilter") String labelFilter) {
        List<Namespace> namespaces = namespacesBean.list(labelFilter);
        return new WanakuResponse<>(namespaces);
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
}
