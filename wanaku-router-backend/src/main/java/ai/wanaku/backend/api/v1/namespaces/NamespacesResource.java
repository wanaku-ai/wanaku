package ai.wanaku.backend.api.v1.namespaces;

import ai.wanaku.api.types.Namespace;
import ai.wanaku.api.types.WanakuResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@ApplicationScoped
@Path("/api/v1/namespaces")
public class NamespacesResource {

    @Inject
    NamespacesBean namespacesBean;

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<Namespace>> list() {
        List<Namespace> namespaces = namespacesBean.list();
        return new WanakuResponse<>(namespaces);
    }
}
