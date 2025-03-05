package ai.wanaku.server.quarkus.api.v1.management.targets;

import ai.wanaku.api.exceptions.ConfigurationNotFoundException;
import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.management.Service;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestPath;

import java.io.IOException;
import java.util.Map;

@ApplicationScoped
@Path("/api/v1/management/targets")
public class TargetsResource {
    private static final Logger LOG = Logger.getLogger(TargetsResource.class);

    @Inject
    TargetsBean targetsBean;

    @Path("/tools/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    public WanakuResponse<Map<String, Service>> toolList() {
        return new WanakuResponse<>(targetsBean.toolList());
    }

    @Path("/tools/configure/{service}")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    public Response toolsConfigure(@RestPath("service") String service, @QueryParam("option") String option,
                                   @QueryParam("value") String value) throws IOException {
        targetsBean.configureTools(service, option, value);
        return Response.ok().build();
    }

    @Path("/resources/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    public WanakuResponse<Map<String,Service>> resourcesList() {
        return new WanakuResponse<>(targetsBean.resourcesList());
    }

    @Path("/resources/configure/{service}")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    public Response resourcesConfigure(@RestPath("service") String service, @QueryParam("option") String option, @QueryParam("value") String value) throws ServiceNotFoundException, IOException, ConfigurationNotFoundException {
        targetsBean.configureResources(service, option, value);
        return Response.ok().build();
    }
}
